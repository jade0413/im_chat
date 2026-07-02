import 'dart:typed_data';

/// 待发送消息任务。展示态在 Message，投递态在 OutboxMessage。
class OutboxMessage {
  const OutboxMessage({
    required this.clientMsgId,
    required this.convId,
    required this.frameBody,
    required this.createdAtMs,
    this.attempts = 0,
    this.nextRetryAtMs,
    this.lastError,
  });

  final String clientMsgId;
  final String convId;
  final Uint8List frameBody;
  final int createdAtMs;
  final int attempts;
  final int? nextRetryAtMs;
  final String? lastError;
}
