/// 应用生命周期钩子说明。
///
/// 当前实际接入点在 `LumoApp.didChangeAppLifecycleState`：
/// App 回前台时调用 `ImEngine.onAppResumed()`，连接层会立即探活或重连。
/// 后续如果要增加后台保活、通知唤醒、桌面托盘恢复，优先在本目录扩展。
abstract final class AppLifecycleContract {
  static const resumedReconnect = 'im_engine_resume_reconnect';
}
