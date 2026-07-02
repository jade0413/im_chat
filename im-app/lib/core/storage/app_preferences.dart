/// 轻量偏好设置 key。
///
/// token/deviceId 放 secure storage；非敏感 UI 偏好和远程配置快照后续放这里。
abstract final class AppPreferenceKeys {
  static const themeMode = 'theme_mode';
  static const locale = 'locale';
  static const lastUpdateCheckAtMs = 'last_update_check_at_ms';
}
