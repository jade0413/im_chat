import 'package:dio/dio.dart';

import '../../../core/platform/platform_info.dart';
import '../../models/session_user.dart';
import 'api_client.dart';
import 'dto.dart';

/// 鉴权 REST（协议 §5）。登录/注册带 platform，服务端按平台类递增 token_ver（D27）。
class AuthApi {
  AuthApi(this._client);
  final ApiClient _client;
  Dio get _dio => _client.dio;

  Future<LoginResult> login({
    required String account,
    required String password,
  }) async {
    final resp = await _dio.post<Map<String, dynamic>>(
      '/api/v1/auth/login',
      data: {
        'account': account,
        'password': password,
        'platform': PlatformInfo.current.protoValue,
      },
    );
    return LoginResult.fromJson(resp.data ?? const {});
  }

  Future<LoginResult> register({
    required String account,
    required String password,
    String? nickname,
  }) async {
    final resp = await _dio.post<Map<String, dynamic>>(
      '/api/v1/auth/register',
      data: {
        'account': account,
        'password': password,
        if (nickname != null) 'nickname': nickname,
        'platform': PlatformInfo.current.protoValue,
      },
    );
    return LoginResult.fromJson(resp.data ?? const {});
  }

  Future<SessionUser> me() async {
    final resp = await _dio.get<Map<String, dynamic>>('/api/v1/users/me');
    return SessionUser.fromJson(resp.data ?? const {});
  }

  /// 修改个人资料：昵称（展示名）+ 头像（可选）。
  Future<void> updateProfile({required String nickname, String? avatar}) async {
    await _dio.put<dynamic>(
      '/api/v1/users/me/profile',
      data: {
        'nickname': nickname.trim(),
        if (avatar != null) 'avatar': avatar,
      },
    );
  }
}
