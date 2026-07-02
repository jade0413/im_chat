/// 连接状态（自有重连设计，见 docs/ARCHITECTURE.md §重连状态机）。
enum ConnectionState {
  idle,
  connecting,
  authenticating,
  connected,
  reconnecting,
  closed,
  error
}

/// 单条消息完整状态机（验收要求 #7）：
/// pending   —— 已入 Outbox，但连接未就绪未发出（断网/未登录）
/// sending   —— 已写入网关，等待 MSG_SEND_ACK
/// sent      —— 服务端已确认并分配 seq（落库成功）
/// delivered —— 对端设备已收到（预留：二阶段送达回执驱动）
/// read      —— 对端已读（READ_NOTIFY 驱动，气泡按 peerReadSeq 推导）
/// failed    —— 发送失败（被拒/超时/重试耗尽），可手动重试
/// revoked   —— 已撤回（本人/审核/管理员）
///
/// 注意：枚举顺序即 DB 存储的 index，新增状态只能往后追加，禁止插中间（兼容旧库）。
enum MessageStatus { pending, sending, sent, delivered, read, failed, revoked }

extension MessageStatusX on MessageStatus {
  bool get isOutbound =>
      this == MessageStatus.pending ||
      this == MessageStatus.sending ||
      this == MessageStatus.failed;
  bool get isAcked =>
      this == MessageStatus.sent ||
      this == MessageStatus.delivered ||
      this == MessageStatus.read;
}

/// 消息内容大类（与 MsgContent oneof 对应 + video 由 file/mime 推导）。
enum ContentKind { text, image, voice, file, video, notification, custom }

/// 会话类型，数值对齐 im-proto common/enums.proto ConvType。
class ConvTypeValue {
  ConvTypeValue._();
  static const int unspecified = 0;
  static const int c2c = 1;
  static const int group = 2;
  static const int csSession = 3;
  static const int system = 4;
}
