/// WebSocket 鉴权恢复策略边界。
///
/// `ImSocket` 通过 TokenRefreshResult 区分 token 真失效和网络错误，网络抖动不会
/// 误触发登出。后续如需设备指纹、双 token 轮换、租户级强制下线，在此扩展 DTO。
class WsAuthResumeRequest {
  const WsAuthResumeRequest({
    required this.accessToken,
    required this.deviceId,
    required this.tenantId,
    required this.platform,
  });

  final String accessToken;
  final String deviceId;
  final int tenantId;
  final int platform;
}
