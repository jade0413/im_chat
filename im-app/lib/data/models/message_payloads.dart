import 'dart:convert';

import 'chat_message.dart';
import 'enums.dart';
import 'message_content.dart';
import 'system_notification.dart';

const quoteReplyType = 'quote.reply';
const mergeForwardType = 'merge.forward';

MessageContent forwardableContent(MessageContent content) => switch (content) {
      TextBody(:final text, :final atUserIds) =>
        TextBody(text, atUserIds: atUserIds),
      ImageBody() => ImageBody(
          objectKey: content.objectKey,
          thumbKey: content.thumbKey,
          width: content.width,
          height: content.height,
          size: content.size,
          mime: content.mime,
        ),
      VoiceBody() => VoiceBody(
          objectKey: content.objectKey,
          durationMs: content.durationMs,
          size: content.size,
          codec: content.codec,
        ),
      FileBody() => FileBody(
          objectKey: content.objectKey,
          fileName: content.fileName,
          size: content.size,
          mime: content.mime,
        ),
      VideoBody() => VideoBody(
          objectKey: content.objectKey,
          fileName: content.fileName,
          size: content.size,
          mime: content.mime,
          thumbKey: content.thumbKey,
          durationMs: content.durationMs,
        ),
      NotificationBody() => CustomBody(
          customType: 'forward.notification',
          payload: jsonEncode({
            'eventType': content.eventType,
            'payload': content.payload,
          }),
        ),
      CustomBody(:final customType, :final payload) =>
        CustomBody(customType: customType, payload: payload),
    };

String messagePreview(MessageContent content) => switch (content) {
      TextBody(:final text) => text,
      ImageBody() => '[图片]',
      VoiceBody(:final durationMs) =>
        durationMs > 0 ? '[语音 ${_duration(durationMs)}]' : '[语音]',
      FileBody(:final fileName) => '[文件] $fileName',
      VideoBody(:final fileName) => '[视频] $fileName',
      NotificationBody() => systemNotificationText(content),
      CustomBody() => customPreview(content),
    };

CustomBody quoteReplyContent({
  required ChatMessage quoted,
  required String text,
}) =>
    CustomBody(
      customType: quoteReplyType,
      payload: jsonEncode({
        'text': text,
        'quote': _quoteMap(quoted),
      }),
    );

CustomBody mergeForwardContent(ChatMessage message) =>
    mergeForwardContentFromMessages([message]);

CustomBody mergeForwardContentFromMessages(List<ChatMessage> messages) {
  final items = _orderedForwardMessages(messages)
      .map(
        (message) => {
          'clientMsgId': message.clientMsgId,
          'serverMsgId': message.serverMsgId,
          'seq': message.seq,
          'convId': message.convId,
          'senderId': message.sender.userId,
          'senderName': message.sender.nickname,
          'sendTime': message.sendTime,
          'kind': message.content.kind.name,
          'preview': messagePreview(message.content),
        },
      )
      .toList();
  return CustomBody(
    customType: mergeForwardType,
    payload: jsonEncode({
      'title': '合并转发 ${items.length} 条消息',
      'items': items,
    }),
  );
}

bool isForwardableMessage(ChatMessage message) {
  if (message.isRevoked || message.isNotification) return false;
  final content = message.content;
  if (message.status == MessageStatus.failed ||
      message.status == MessageStatus.sending ||
      message.status == MessageStatus.pending) {
    return content is TextBody || content is CustomBody;
  }
  return switch (content) {
    TextBody() || CustomBody() => true,
    ImageBody(:final objectKey) => objectKey.isNotEmpty,
    VoiceBody(:final objectKey) => objectKey.isNotEmpty,
    FileBody(:final objectKey) => objectKey.isNotEmpty,
    VideoBody(:final objectKey) => objectKey.isNotEmpty,
    NotificationBody() => false,
  };
}

QuoteReplyPayload? parseQuoteReply(CustomBody body) {
  if (body.customType != quoteReplyType) return null;
  final map = _decode(body.payload);
  if (map == null) return null;
  final quote = map['quote'];
  final quoteMap =
      quote is Map<Object?, Object?> ? quote : const <Object?, Object?>{};
  return QuoteReplyPayload(
    text: (map['text'] ?? '').toString(),
    senderName: (quoteMap['senderName'] ?? '').toString(),
    preview: (quoteMap['preview'] ?? '').toString(),
  );
}

MergeForwardPayload? parseMergeForward(CustomBody body) {
  if (body.customType != mergeForwardType) return null;
  final map = _decode(body.payload);
  if (map == null) return null;
  final rawItems = (map['items'] as List?) ?? const [];
  return MergeForwardPayload(
    title: (map['title'] ?? '合并转发').toString(),
    items: rawItems
        .whereType<Map<Object?, Object?>>()
        .map((item) => MergeForwardItem(
              senderName: (item['senderName'] ?? '').toString(),
              preview: (item['preview'] ?? '').toString(),
              kind: (item['kind'] ?? '').toString(),
              sendTime: (item['sendTime'] ?? '').toString(),
            ))
        .toList(),
  );
}

String customPreview(CustomBody body) {
  final quote = parseQuoteReply(body);
  if (quote != null) return quote.text.isEmpty ? '[引用回复]' : quote.text;
  final merge = parseMergeForward(body);
  if (merge != null) return '[合并转发] ${merge.title}';
  return '[${body.customType}]';
}

Map<String, Object?> _quoteMap(ChatMessage message) => {
      'clientMsgId': message.clientMsgId,
      'serverMsgId': message.serverMsgId,
      'seq': message.seq,
      'convId': message.convId,
      'senderId': message.sender.userId,
      'senderName': message.sender.nickname,
      'kind': message.content.kind.name,
      'preview': messagePreview(message.content),
    };

Map<String, Object?>? _decode(String? raw) {
  if (raw == null || raw.isEmpty) return null;
  try {
    final value = jsonDecode(raw);
    return value is Map ? value.cast<String, Object?>() : null;
  } catch (_) {
    return null;
  }
}

String _duration(int ms) {
  final seconds = (ms / 1000).round();
  return '$seconds"';
}

List<ChatMessage> _orderedForwardMessages(List<ChatMessage> messages) {
  final filtered = messages.where(isForwardableMessage).toList();
  filtered.sort((a, b) {
    final seqA = BigInt.tryParse(a.seq ?? '');
    final seqB = BigInt.tryParse(b.seq ?? '');
    if (seqA != null && seqB != null && seqA != seqB) {
      return seqA.compareTo(seqB);
    }
    final timeA = int.tryParse(a.sendTime) ?? 0;
    final timeB = int.tryParse(b.sendTime) ?? 0;
    if (timeA != timeB) return timeA.compareTo(timeB);
    return a.clientMsgId.compareTo(b.clientMsgId);
  });
  return filtered;
}

class QuoteReplyPayload {
  const QuoteReplyPayload({
    required this.text,
    required this.senderName,
    required this.preview,
  });

  final String text;
  final String senderName;
  final String preview;
}

class MergeForwardPayload {
  const MergeForwardPayload({required this.title, required this.items});

  final String title;
  final List<MergeForwardItem> items;
}

class MergeForwardItem {
  const MergeForwardItem({
    required this.senderName,
    required this.preview,
    required this.kind,
    required this.sendTime,
  });

  final String senderName;
  final String preview;
  final String kind;
  final String sendTime;
}
