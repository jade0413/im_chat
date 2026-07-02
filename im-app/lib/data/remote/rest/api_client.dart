import 'dart:async';
import 'package:dio/dio.dart';

import '../../../core/config/env.dart';
import '../../../core/logging.dart';
import '../token_refresh.dart';
import 'dto.dart';
import 'token_store.dart';

/// REST 客户端（dio）。
///
/// - 注入 Authorization: Bearer + X-Tenant-Id（协议 §5）
/// - 401 自动刷新一次 token 后重放原请求；刷新用独立 dio 避免拦截器递归
/// - 单飞（single-flight）：并发 401 只触发一次刷新，其余等待同一个 Future
class ApiClient {
  ApiClient(this._tokens, {this.onAuthExpired}) {
    _dio = Dio(_baseOptions());
    _refreshDio = Dio(_baseOptions());
    // 信封解包：后端统一返回 ApiResponse{code,message,data,...}，
    // 解包后各 API 直接拿 data。两个 dio 都要装（refresh 也走信封）。
    _dio.interceptors.add(_envelopeInterceptor());
    _refreshDio.interceptors.add(_envelopeInterceptor());
    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: _onRequest,
        onError: _onError,
      ),
    );
  }

  final TokenStore _tokens;

  /// 刷新彻底失败（refresh 也 401/403）时回调——上层据此登出。
  final FutureOr<void> Function()? onAuthExpired;

  final _log = appLogger('rest');
  late final Dio _dio;
  late final Dio _refreshDio;
  Future<TokenRefreshResult>? _refreshing; // 单飞

  Dio get dio => _dio;

  /// 解包 ApiResponse 信封：{code,message,data,...} → data。
  /// code != 0（OK）时抛 ApiException 让上层捕获展示。
  Interceptor _envelopeInterceptor() => InterceptorsWrapper(
        onResponse: (response, handler) {
          final data = response.data;
          if (data is Map &&
              data.containsKey('code') &&
              data.containsKey('data')) {
            final code = (data['code'] as num?)?.toInt() ?? 0;
            if (code != 0) {
              handler.reject(DioException(
                requestOptions: response.requestOptions,
                response: response,
                type: DioExceptionType.badResponse,
                error: ApiException(
                    code, (data['message'] ?? '请求失败').toString()),
              ));
              return;
            }
            response.data = data['data'];
          }
          handler.next(response);
        },
      );

  BaseOptions _baseOptions() => BaseOptions(
        baseUrl: Env.apiBaseUrl,
        connectTimeout: const Duration(seconds: 10),
        receiveTimeout: const Duration(seconds: 20),
        headers: {'X-Tenant-Id': Env.tenantId.toString()},
        // 业务错误码由各 API 解析，这里只在 2xx 视为成功
        validateStatus: (s) => s != null && s < 400,
      );

  Future<void> _onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    final token = await _tokens.readAccessToken();
    if (token != null && token.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  Future<void> _onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    final res = err.response;
    final isAuth = res?.statusCode == 401;
    final alreadyRetried = err.requestOptions.extra['__retried__'] == true;
    if (!isAuth || alreadyRetried) {
      return handler.next(err);
    }

    final result = await _refreshOnce();
    if (result != TokenRefreshResult.success) {
      // 仅在凭证真失效时登出；网络错误交给调用方自行重试，不强制下线
      if (result == TokenRefreshResult.authInvalid) await onAuthExpired?.call();
      return handler.next(err);
    }

    // 用新 token 重放原请求
    try {
      final token = await _tokens.readAccessToken();
      final opts = err.requestOptions
        ..extra['__retried__'] = true
        ..headers['Authorization'] = 'Bearer $token';
      final clone = await _dio.fetch<dynamic>(opts);
      return handler.resolve(clone);
    } catch (e) {
      return handler.next(err);
    }
  }

  /// 供 WS AUTH 失败时复用：刷新一次 token（单飞），返回三态结果。
  Future<TokenRefreshResult> forceRefresh() => _refreshOnce();

  /// 单飞刷新：并发调用共享同一个 Future。
  Future<TokenRefreshResult> _refreshOnce() {
    return _refreshing ??= _doRefresh().whenComplete(() => _refreshing = null);
  }

  Future<TokenRefreshResult> _doRefresh() async {
    final refreshToken = await _tokens.readRefreshToken();
    if (refreshToken == null || refreshToken.isEmpty) {
      return TokenRefreshResult.authInvalid;
    }
    try {
      final resp = await _refreshDio.post<Map<String, dynamic>>(
        '/api/v1/auth/refresh',
        data: {'refreshToken': refreshToken},
      );
      final bundle = TokenBundle.fromJson(resp.data ?? const {});
      if (bundle.accessToken.isEmpty) return TokenRefreshResult.authInvalid;
      await _tokens.saveTokens(
        accessToken: bundle.accessToken,
        refreshToken:
            bundle.refreshToken.isEmpty ? refreshToken : bundle.refreshToken,
      );
      _log.info('access token refreshed');
      return TokenRefreshResult.success;
    } on DioException catch (e) {
      final code = e.response?.statusCode;
      _log.warning('token refresh failed status=$code');
      // 401/403 → 凭证真失效；其余（超时/无网/5xx）→ 网络错误，稍后重试
      if (code == 401 || code == 403) return TokenRefreshResult.authInvalid;
      return TokenRefreshResult.networkError;
    }
  }
}

/// 业务错误（ApiResponse.code != 0）。
class ApiException implements Exception {
  ApiException(this.code, this.message);
  final int code;
  final String message;
  @override
  String toString() => 'ApiException($code, $message)';
}
