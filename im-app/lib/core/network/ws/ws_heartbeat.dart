/// WebSocket 心跳策略常量。
///
/// 实际发送和半死链探测在 `ImSocket._startHeartbeat` 内执行；这里保留稳定配置
/// 边界，后续可接 Remote Config 做租户/环境级调整。
abstract final class WsHeartbeatDefaults {
  static const fallbackIntervalMs = 30000;
  static const livenessFactor = 2.5;
}
