import 'dart:typed_data';

import 'models/message_content.dart';
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
  }) async {
    final presign = await _fileApi.presign(
      fileName: fileName,
      mime: mime,
      size: bytes.length,
    );
    await _fileApi.uploadDirect(presign, bytes);
    await _messages.sendImage(
      convId,
      ImageBody(
        objectKey: presign.objectKey,
        size: bytes.length,
        mime: mime,
        width: width,
        height: height,
        localPath: localPath,
      ),
    );
  }

  /// 上传并发送任意文件。
  Future<void> sendFile(
    String convId, {
    required Uint8List bytes,
    required String fileName,
    required String mime,
  }) async {
    final presign = await _fileApi.presign(
      fileName: fileName,
      mime: mime,
      size: bytes.length,
    );
    await _fileApi.uploadDirect(presign, bytes);
    await _messages.sendFile(
      convId,
      FileBody(
        objectKey: presign.objectKey,
        fileName: fileName,
        size: bytes.length,
        mime: mime,
      ),
    );
  }
}
