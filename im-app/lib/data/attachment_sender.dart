import 'dart:typed_data';

import 'file_hash.dart';
import 'media_preprocessor.dart';
import 'models/message_content.dart';
import 'remote/rest/api_client.dart';
import 'remote/rest/file_api.dart';
import 'repositories/message_repository.dart';

/// 富媒体发送编排（D10）：先 presign 申请凭证 → 直传 MinIO → 再走消息发送链路。
/// UI 只调用这里，不感知上传细节；文件流量不过业务服务器。
class AttachmentSender {
  AttachmentSender(this._fileApi, this._messages);

  final FileApi _fileApi;
  final MessageRepository _messages;

  /// 上传并发送图片。localPath 供发送方乐观预览（对方仍走 objectKey）。
  Future<void> sendImage(
    String convId, {
    required Uint8List bytes,
    required String fileName,
    required String mime,
    String? localPath,
    int? width,
    int? height,
    AttachmentProgressCallback? onProgress,
  }) async {
    final thumb = await _runUploadStep(
      '生成图片缩略图',
      () async => MediaPreprocessor.imageThumbnail(bytes),
      onProgress: onProgress,
    );
    final source = await _uploadObject(
      label: '上传原图',
      fileName: fileName,
      mime: mime,
      bytes: bytes,
      onProgress: onProgress,
    );
    final uploadedThumb = thumb == null
        ? null
        : await _uploadObject(
            label: '上传图片缩略图',
            fileName: MediaPreprocessor.thumbnailFileName(fileName),
            mime: 'image/jpeg',
            bytes: thumb.bytes,
            onProgress: onProgress,
          );
    await _runUploadStep(
      '发送图片消息',
      () => _messages.sendImage(
        convId,
        ImageBody(
          objectKey: source.objectKey,
          thumbKey: uploadedThumb?.objectKey,
          size: bytes.length,
          mime: mime,
          width: width ?? thumb?.width,
          height: height ?? thumb?.height,
          localPath: localPath,
        ),
      ),
      onProgress: onProgress,
    );
  }

  /// 上传并发送任意文件。
  Future<void> sendFile(
    String convId, {
    required Uint8List bytes,
    required String fileName,
    required String mime,
    AttachmentProgressCallback? onProgress,
  }) async {
    final uploaded = await _uploadObject(
      label: '上传文件',
      fileName: fileName,
      mime: mime,
      bytes: bytes,
      onProgress: onProgress,
    );
    await _runUploadStep(
      '发送文件消息',
      () => _messages.sendFile(
        convId,
        FileBody(
          objectKey: uploaded.objectKey,
          fileName: fileName,
          size: bytes.length,
          mime: mime,
        ),
      ),
      onProgress: onProgress,
    );
  }

  /// 上传并发送视频消息。协议层复用 FileContent，客户端按 video/* MIME 渲染。
  Future<void> sendVideo(
    String convId, {
    required Uint8List bytes,
    required String fileName,
    required String mime,
    String? localPath,
    AttachmentProgressCallback? onProgress,
  }) async {
    final metadata = await _runUploadStep(
      '生成视频封面',
      () => MediaPreprocessor.videoMetadata(localPath),
      onProgress: onProgress,
    );
    final video = await _uploadObject(
      label: '上传视频',
      fileName: fileName,
      mime: mime,
      bytes: bytes,
      durationMs: metadata.durationMs,
      onProgress: onProgress,
    );
    final thumb = metadata.thumbnail == null
        ? null
        : await _uploadObject(
            label: '上传视频封面',
            fileName: MediaPreprocessor.thumbnailFileName(fileName),
            mime: 'image/jpeg',
            bytes: metadata.thumbnail!.bytes,
            onProgress: onProgress,
          );
    await _runUploadStep(
      '发送视频消息',
      () => _messages.sendVideo(
        convId,
        VideoBody(
          objectKey: video.objectKey,
          fileName: fileName,
          size: bytes.length,
          mime: mime,
          thumbKey: thumb?.objectKey,
          durationMs: metadata.durationMs,
          localPath: localPath,
        ),
      ),
      onProgress: onProgress,
    );
  }

  /// 上传并发送语音消息。localPath 供本机发送后立即播放/排查。
  Future<void> sendVoice(
    String convId, {
    required Uint8List bytes,
    required String fileName,
    required String mime,
    required int durationMs,
    required String codec,
    String? localPath,
    AttachmentProgressCallback? onProgress,
  }) async {
    final uploaded = await _uploadObject(
      label: '上传语音',
      fileName: fileName,
      mime: mime,
      bytes: bytes,
      durationMs: durationMs,
      onProgress: onProgress,
    );
    await _runUploadStep(
      '发送语音消息',
      () => _messages.sendVoice(
        convId,
        VoiceBody(
          objectKey: uploaded.objectKey,
          durationMs: durationMs,
          size: bytes.length,
          codec: codec,
          localPath: localPath,
        ),
      ),
      onProgress: onProgress,
    );
  }

  Future<_UploadedObject> _uploadObject({
    required String label,
    required String fileName,
    required String mime,
    required Uint8List bytes,
    int? durationMs,
    AttachmentProgressCallback? onProgress,
  }) async {
    final sha256 = await sha256Hex(bytes);
    final presign = await _runUploadStep(
      '$label：申请上传凭证',
      () => _fileApi.presign(
        fileName: fileName,
        mime: mime,
        size: bytes.length,
        durationMs: durationMs,
        sha256: sha256,
      ),
      onProgress: onProgress,
    );
    if (presign.instant) {
      _emitProgress(onProgress, '$label：命中秒传');
      return _UploadedObject(presign.objectKey);
    }
    await _runUploadStep(
      '$label：上传到对象存储',
      () => _withRetry(
        label: label,
        task: (attempt) => _fileApi.uploadDirect(
          presign,
          bytes,
          onSendProgress: (sent, total) => _emitProgress(
            onProgress,
            '$label：上传中',
            sentBytes: sent,
            totalBytes: total > 0 ? total : bytes.length,
            attempt: attempt,
          ),
        ),
        onRetry: (attempt) => _emitProgress(
          onProgress,
          '$label：上传失败，重试 $attempt/3',
          sentBytes: 0,
          totalBytes: bytes.length,
          attempt: attempt,
        ),
      ),
      onProgress: onProgress,
    );
    await _runUploadStep(
      '$label：确认上传结果',
      () => _fileApi.confirm(
        objectKey: presign.objectKey,
        size: bytes.length,
        mime: mime,
      ),
      onProgress: onProgress,
    );
    return _UploadedObject(presign.objectKey);
  }

  Future<T> _runUploadStep<T>(
    String label,
    Future<T> Function() task, {
    AttachmentProgressCallback? onProgress,
  }) async {
    _emitProgress(onProgress, label);
    try {
      return await task();
    } catch (e) {
      throw AttachmentSendException('$label失败：${describeApiError(e)}');
    }
  }

  Future<T> _withRetry<T>({
    required String label,
    required Future<T> Function(int attempt) task,
    required void Function(int attempt) onRetry,
  }) async {
    const maxAttempts = 3;
    for (var attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return await task(attempt);
      } catch (_) {
        if (attempt == maxAttempts) rethrow;
        onRetry(attempt + 1);
        await Future<void>.delayed(Duration(milliseconds: 350 * attempt));
      }
    }
    throw AttachmentSendException('$label失败');
  }

  void _emitProgress(
    AttachmentProgressCallback? onProgress,
    String label, {
    int? sentBytes,
    int? totalBytes,
    int attempt = 1,
  }) {
    onProgress?.call(AttachmentTransferProgress(
      label: label,
      sentBytes: sentBytes,
      totalBytes: totalBytes,
      attempt: attempt,
    ));
  }
}

typedef AttachmentProgressCallback = void Function(
  AttachmentTransferProgress progress,
);

class AttachmentTransferProgress {
  const AttachmentTransferProgress({
    required this.label,
    this.sentBytes,
    this.totalBytes,
    this.attempt = 1,
  });

  final String label;
  final int? sentBytes;
  final int? totalBytes;
  final int attempt;

  double? get fraction {
    final total = totalBytes;
    final sent = sentBytes;
    if (total == null || total <= 0 || sent == null) return null;
    return (sent / total).clamp(0.0, 1.0).toDouble();
  }
}

class _UploadedObject {
  const _UploadedObject(this.objectKey);
  final String objectKey;
}

class AttachmentSendException implements Exception {
  AttachmentSendException(this.message);
  final String message;

  @override
  String toString() => message;
}
