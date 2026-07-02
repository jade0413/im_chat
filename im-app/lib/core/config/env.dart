/// 运行期配置。生产用 `--dart-define`（或 --dart-define-from-file）注入：
///
///   flutter run \
///     --dart-define=TENANT_ID=1 \
///     --dart-define=API_BASE_URL=https://im.example.com \
///     --dart-define=WS_URL=wss://im.example.com/ws
///
/// 不传则用默认值（对齐当前可用的开发服务器）。
class Env {
  Env._();

  /// 主租户模式（D21）：自营 App 即 tenant 1；运行期恒定，随处透传。
  static const int tenantId = int.fromEnvironment('TENANT_ID', defaultValue: 1);

  /// 写入 AuthReq.app_version；也是桌面端 OTA 的「当前版本」比较基准之一。
  static const String appVersion =
      String.fromEnvironment('APP_VERSION', defaultValue: '0.1.0');

  static const String _apiBaseRaw =
      String.fromEnvironment('API_BASE_URL', defaultValue: '');

  static const String _wsUrlRaw =
      String.fromEnvironment('WS_URL', defaultValue: '');

  /// 桌面端 OTA：版本清单地址（返回最新版本号 + 下载链接），见 update_service.dart。
  static const String desktopUpdateManifestUrl =
      String.fromEnvironment('DESKTOP_UPDATE_URL', defaultValue: '');

  /// REST API 根地址（末尾不带斜杠）。
  static String get apiBaseUrl =>
      _apiBaseRaw.isNotEmpty ? _apiBaseRaw : 'http://103.45.65.84:8081';

  /// 网关 WebSocket 地址。
  static String get wsUrl =>
      _wsUrlRaw.isNotEmpty ? _wsUrlRaw : 'ws://103.45.65.84:8082/ws';

  // ── 安全存储 key（flutter_secure_storage）──────────────
  static const String kAccessToken = 'im_access_token';
  static const String kRefreshToken = 'im_refresh_token';
  static const String kDeviceId = 'im_device_id';
  static const String kCurrentUserId = 'im_current_user_id';
}
