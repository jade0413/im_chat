import 'package:flutter_test/flutter_test.dart';
import 'package:im_app/data/models/chat_message.dart';
import 'package:im_app/data/models/enums.dart';
import 'package:im_app/data/models/message_content.dart';
import 'package:im_app/data/models/message_payloads.dart';
import 'package:im_app/data/models/sender_info.dart';

void main() {
  test('merge forward keeps only forwardable messages ordered by seq', () {
    final later = _message(
      clientMsgId: 'c2',
      seq: '20',
      senderName: '李四',
      content: const TextBody('后发'),
    );
    final earlier = _message(
      clientMsgId: 'c1',
      seq: '10',
      senderName: '张三',
      content: const ImageBody(objectKey: '1/202607/a.png'),
    );
    final revoked = _message(
      clientMsgId: 'c3',
      seq: '30',
      senderName: '王五',
      status: MessageStatus.revoked,
      content: const TextBody('已撤回'),
    );
    final notification = _message(
      clientMsgId: 'c4',
      seq: '40',
      senderName: '系统',
      content: const NotificationBody(eventType: 'message.revoked'),
    );

    expect(isForwardableMessage(later), isTrue);
    expect(isForwardableMessage(revoked), isFalse);
    expect(isForwardableMessage(notification), isFalse);

    final body = mergeForwardContentFromMessages([
      later,
      revoked,
      notification,
      earlier,
    ]);
    final payload = parseMergeForward(body);

    expect(payload, isNotNull);
    expect(payload!.title, '合并转发 2 条消息');
    expect(payload.items.map((item) => item.senderName), ['张三', '李四']);
    expect(payload.items.map((item) => item.preview), ['[图片]', '后发']);
    expect(payload.items.map((item) => item.kind), ['image', 'text']);
    expect(payload.items.map((item) => item.sendTime), ['1010', '1020']);
  });
}

ChatMessage _message({
  required String clientMsgId,
  required String seq,
  required String senderName,
  required MessageContent content,
  MessageStatus status = MessageStatus.sent,
}) =>
    ChatMessage(
      clientMsgId: clientMsgId,
      serverMsgId: 's$clientMsgId',
      seq: seq,
      convId: '1',
      sender: SenderInfo(userId: 'u$clientMsgId', nickname: senderName),
      content: content,
      sendTime: (1000 + int.parse(seq)).toString(),
      status: status,
    );
