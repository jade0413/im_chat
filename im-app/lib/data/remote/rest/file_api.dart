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
  }) async {
    final resp = await _client.dio.post<Map<String, dynamic>>(
      '/api/v1/files/presign',
      data: {'fileName': fileName, 'mime': mime, 'size': size},
    );
    return PresignResult.fromJson(resp.data ?? const {});
  }

  /// PUT 直传到 MinIO 预签名 URL（不带我们的 Authorization，用裸 Dio）。
  Future<void> uploadDirect(PresignResult presign, Uint8List bytes) async {
    final raw = Dio();
    await raw.put<void>(
      presign.uploadUrl,
      data: Stream.fromIterable([bytes]),
      options: Options(
        headers: {
          ...presign.headers,
          Headers.contentLengthHeader: bytes.length,
        },
      ),
    );
  }
}
