/// 接收消息用例边界。
///
/// 当前 MSG_PUSH/SYNC_RESP 由 `ImEngine` 统一解码、去重、事务落库和 ACK。
/// 后续如果需要可测试的纯领域处理器，可以把 `ImEngine._handleMsgPush` 迁入这里。
class ReceiveMessage {
  const ReceiveMessage();
}
