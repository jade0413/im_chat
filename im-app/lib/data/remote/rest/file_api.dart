import 'dart:io';
import 'dart:typed_data';
import 'package:dio/dio.dart';

import 'api_client.dart';
import 'dto.dart';

/// 文件直传（D10：im-file-service 只发预签名凭证 + 落元数据，文件流量不过业务服务器）。
class FileApi {
  FileApi(this._client);
  final ApiClient _client;

  /// 申请上传凭证。
  Future<PresignResult> presign({
    required String fileName,
    required String mime,
    required int size,
    int? durationMs,
    String? sha256,
  }) async {
    final resp = await _client.dio.post<Map<String, dynamic>>(
      '/api/v1/files/presign',
      data: {
        'fileName': fileName,
        'mime': mime,
        'size': size,
        if (durationMs != null && durationMs > 0) 'durationMs': durationMs,
        if (sha256 != null && sha256.isNotEmpty) 'sha256': sha256,
      },
    );
    return PresignResult.fromJson(resp.data ?? const {});
  }

  /// PUT 直传到 MinIO 预签名 URL（不带我们的 Authorization，用裸 Dio）。
  Future<void> uploadDirect(
    PresignResult presign,
    Uint8List bytes, {
    ProgressCallback? onSendProgress,
  }) async {
    final raw = Dio();
    final contentType =
        _headerValue(presign.headers, Headers.contentTypeHeader);
    try {
      await raw.put<void>(
        presign.uploadUrl,
        data: Stream.fromIterable([bytes]),
        options: Options(
          contentType: contentType,
          headers: {
            ...presign.headers,
            Headers.contentLengthHeader: bytes.length,
          },
        ),
        onSendProgress: onSendProgress,
      );
    } finally {
      raw.close();
    }
  }

  String? _headerValue(Map<String, String> headers, String name) {
    final lowerName = name.toLowerCase();
    for (final entry in headers.entries) {
      if (entry.key.toLowerCase() == lowerName) return entry.value;
    }
    return null;
  }

  /// 确认对象已成功写入 MinIO，服务端会校验 size/mime 并把 file_meta 标记为 CONFIRMED。
  Future<void> confirm({
    required String objectKey,
    int? size,
    String? mime,
  }) async {
    await _client.dio.post<dynamic>(
      '/api/v1/files/confirm',
      data: {
        'objectKey': objectKey,
        if (size != null) 'size': size,
        if (mime != null && mime.isNotEmpty) 'mime': mime,
      },
    );
  }

  /// 获取临时下载 URL，用于图片预览、语音/文件打开等只读访问。
  Future<String> downloadUrl(String objectKey, {String? variant}) async {
    final resp = await _client.dio.get<dynamic>(
      '/api/v1/files/download',
      queryParameters: {
        'key': objectKey,
        if (variant != null && variant.isNotEmpty) 'variant': variant,
      },
    );
    return (resp.data ?? '').toString();
  }

  /// 获取后端实际选择的下载对象和 URL。视频 playback 变体会优先返回已完成的转码产物。
  Future<FileDownloadInfo> downloadInfo(
    String objectKey, {
    String? variant,
  }) async {
    final resp = await _client.dio.get<Map<String, dynamic>>(
      '/api/v1/files/download-info',
      queryParameters: {
        'key': objectKey,
        if (variant != null && variant.isNotEmpty) 'variant': variant,
      },
    );
    return FileDownloadInfo.fromJson(resp.data ?? const {});
  }

  /// 下载文件内容。语音播放先落到本地临时文件，避免 macOS AVPlayer 直接播放
  /// 预签名 HTTP URL 时被 ATS/格式探测拦截。
  Future<Uint8List> downloadBytes(String objectKey, {String? variant}) async {
    final url = await downloadUrl(objectKey, variant: variant);
    return downloadUrlBytes(url);
  }

  Future<Uint8List> downloadUrlBytes(String url) async {
    final raw = Dio();
    try {
      final resp = await raw.get<List<int>>(
        url,
        options: Options(responseType: ResponseType.bytes),
      );
      return Uint8List.fromList(resp.data ?? const <int>[]);
    } finally {
      raw.close();
    }
  }

  /// 下载对象到本地文件，用于文件/视频打开时展示确定的下载进度。
  Future<File> downloadToFile(
    String objectKey,
    String savePath, {
    String? variant,
    ProgressCallback? onReceiveProgress,
  }) async {
    final url = await downloadUrl(objectKey, variant: variant);
    return downloadUrlToFile(
      url,
      savePath,
      onReceiveProgress: onReceiveProgress,
    );
  }

  /// 已拿到下载 URL 时直接保存，供需要先解析 resolved objectKey 的视频缓存使用。
  Future<File> downloadUrlToFile(
    String url,
    String savePath, {
    ProgressCallback? onReceiveProgress,
  }) async {
    final raw = Dio();
    try {
      await raw.download(
        url,
        savePath,
        options: Options(responseType: ResponseType.bytes),
        onReceiveProgress: onReceiveProgress,
      );
      return File(savePath);
    } finally {
      raw.close();
    }
  }
}

class FileDownloadInfo {
  const FileDownloadInfo({
    required this.objectKey,
    required this.url,
    required this.expiresAt,
    required this.transformed,
  });

  final String objectKey;
  final String url;
  final int expiresAt;
  final bool transformed;

  factory FileDownloadInfo.fromJson(Map<String, dynamic> json) {
    return FileDownloadInfo(
      objectKey: (json['objectKey'] ?? '').toString(),
      url: (json['url'] ?? '').toString(),
      expiresAt: _intValue(json['expiresAt']),
      transformed: json['transformed'] == true,
    );
  }

  static int _intValue(Object? value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse((value ?? '').toString()) ?? 0;
  }
}
