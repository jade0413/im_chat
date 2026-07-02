import 'enums.dart';
import 'message_content.dart';
import 'sender_info.dart';

/// 一条聊天消息（移植 im-web ChatMessage）。
///
/// id/seq 一律十进制 String。clientMsgId 是稳定主键（乐观消息阶段就有），
/// serverMsgId/seq 在收到 MSG_SEND_ACK 后回填。
class ChatMessage {
  const ChatMessage({
    required this.clientMsgId,
    required this.convId,
    required this.sender,
    required this.content,
    required this.sendTime,
    required this.status,
    this.serverMsgId,
    this.seq,
    this.failCode,
  });

  final String clientMsgId;
  final String? serverMsgId;
  final String? seq; // 会话级 seq——排序/对齐依据
  final String convId;
  final SenderInfo sender;
  final MessageContent content;
  final String sendTime;
  final MessageStatus status;

  /// 发送失败时服务端回带的 ErrorCode（MSG_SEND_ACK.code），用于展示失败原因
  /// （2001=被对方拉黑，2002=需先加好友 …）。
  final int? failCode;

  bool get isRevoked => status == MessageStatus.revoked;
  bool get isNotification => content.kind == ContentKind.notification;

  ChatMessage copyWith({
    String? convId,
    String? serverMsgId,
    String? seq,
    MessageContent? content,
    MessageStatus? status,
    int? failCode,
    bool clearFailCode = false,
  }) =>
      ChatMessage(
        clientMsgId: clientMsgId,
        convId: convId ?? this.convId,
        sender: sender,
        content: content ?? this.content,
        sendTime: sendTime,
        status: status ?? this.status,
        serverMsgId: serverMsgId ?? this.serverMsgId,
        seq: seq ?? this.seq,
        failCode: clearFailCode ? null : (failCode ?? this.failCode),
      );
}
