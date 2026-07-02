import '../../../core/config/env.dart';
import '../../../core/utils/uuid.dart';
import 'credential_storage.dart';

/// 凭证存储。macOS 本地测试默认走本地文件，避免未签名/临时签名环境触发 Keychain 授权错误。
/// 单实例内存缓存 accessToken，避免每次请求都 await 解密。
class TokenStore {
  TokenStore(this._storage);

  final CredentialStorage _storage;

  String? _accessTokenCache;
  String? get accessTokenCache => _accessTokenCache;

  Future<String?> readAccessToken() async {
    return _accessTokenCache ??= await _storage.read(key: Env.kAccessToken);
  }

  Future<String?> readRefreshToken() => _storage.read(key: Env.kRefreshToken);

  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    _accessTokenCache = accessToken;
    await _storage.write(key: Env.kAccessToken, value: accessToken);
    await _storage.write(key: Env.kRefreshToken, value: refreshToken);
  }

  Future<void> clear() async {
    _accessTokenCache = null;
    await _storage.delete(key: Env.kAccessToken);
    await _storage.delete(key: Env.kRefreshToken);
    await _storage.delete(key: Env.kCurrentUserId);
  }

  Future<void> saveUserId(String userId) =>
      _storage.write(key: Env.kCurrentUserId, value: userId);
  Future<String?> readUserId() => _storage.read(key: Env.kCurrentUserId);

  /// 稳定设备号（互踢矩阵按设备区分，D11）。首次生成后持久化。
  Future<String> getOrCreateDeviceId() async {
    final existing = await _storage.read(key: Env.kDeviceId);
    if (existing != null && existing.isNotEmpty) return existing;
    final created = createUuid();
    await _storage.write(key: Env.kDeviceId, value: created);
    return created;
  }
}
