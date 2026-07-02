import 'dart:typed_data';

import 'package:fixnum/fixnum.dart';

import '../../../core/proto/codec.dart' as pb;
import '../../../core/utils/id.dart';
import '../../models/chat_message.dart';
import '../../models/conversation.dart';
import '../../models/enums.dart';
import '../../models/message_content.dart';
import '../../models/sender_info.dart';

/// proto ↔ 领域模型 互转 + 上行帧 body 构造（移植 im-web socket/mappers.ts + ImSocket 构造逻辑）。
class WsMappers {
  WsMappers._();

  // ─── 下行：proto → model ───────────────────────────────

  static ChatMessage msgPushToChatMessage(pb.MsgPush push) => ChatMessage(
        // 去重关键（验收要求 #9）：服务端无 client_msg_id 时用确定性 convId:seq，
        // 保证重连/重复 SYNC 同一条消息得到相同主键 → upsert 幂等，不重复插入。
        clientMsgId: push.clientMsgId.isNotEmpty
            ? push.clientMsgId
            : '${Ids.toStr(push.convId)}:${Ids.toStr(push.seq)}',
        serverMsgId: Ids.toStr(push.serverMsgId),
        seq: Ids.toStr(push.seq),
        convId: Ids.toStr(push.convId),
        sender: senderToInfo(push.hasSender() ? push.sender : null),
        content: protoContentToContent(push.hasContent() ? push.content : null),
        sendTime: Ids.toStr(push.sendTime),
        status: MessageStatus.sent,
      );

  static Conversation convInfoToConversation(pb.ConvInfo c) => Conversation(
        convId: Ids.toStr(c.convId),
        type: c.type.value,
        title: c.title.isNotEmpty ? c.title : '未命名会话',
        avatar: c.avatar.isNotEmpty ? c.avatar : null,
        peerUserId:
            Ids.isZeroOrEmpty(c.peerUserId) ? null : Ids.toStr(c.peerUserId),
        groupId: Ids.isZeroOrEmpty(c.groupId) ? null : Ids.toStr(c.groupId),
        maxSeq: Ids.toStr(c.maxSeq),
        readSeq: Ids.toStr(c.readSeq),
        pinned: c.pinned,
        muted: c.muted,
        lastMsgAbstract: c.lastMsgAbstract,
        lastMsgTime:
            Ids.isZeroOrEmpty(c.lastMsgTime) ? null : Ids.toStr(c.lastMsgTime),
        csStatus: c.csStatus.isNotEmpty ? c.csStatus : null,
      );

  static SenderInfo senderToInfo(pb.Sender? s) => SenderInfo(
        userId: Ids.toStr(s?.userId),
        nickname: (s?.nickname.isNotEmpty ?? false)
            ? s!.nickname
            : '用户 ${Ids.toStr(s?.userId)}',
        avatar: (s?.avatar.isNotEmpty ?? false) ? s!.avatar : null,
        verifiedType: s?.verifiedType.value ?? 0,
        userType: s?.userType.value ?? 0,
      );

  static MessageContent protoContentToContent(pb.MsgContent? content) {
    if (content == null) {
      return const NotificationBody(eventType: 'message.empty');
    }
    if (content.hasText()) {
      return TextBody(
        content.text.text,
        atUserIds: content.text.atUserIds.map(Ids.toStr).toList(),
      );
    }
    if (content.hasImage()) {
      final i = content.image;
      return ImageBody(
        objectKey: i.objectKey,
        thumbKey: i.thumbKey.isNotEmpty ? i.thumbKey : null,
        width: i.width,
        height: i.height,
        size: i.size.toInt(),
        mime: i.mime.isNotEmpty ? i.mime : null,
      );
    }
    if (content.hasVoice()) {
      final v = content.voice;
      return VoiceBody(
        objectKey: v.objectKey,
        durationMs: v.durationMs,
        size: v.size.toInt(),
        codec: v.codec.isNotEmpty ? v.codec : null,
      );
    }
    if (content.hasFile()) {
      final f = content.file;
      final mime = f.mime.isNotEmpty ? f.mime : null;
      if (mime?.startsWith('video/') ?? false) {
        return VideoBody(
          objectKey: f.objectKey,
          fileName: f.fileName,
          size: f.size.toInt(),
          mime: mime,
        );
      }
      return FileBody(
        objectKey: f.objectKey,
        fileName: f.fileName,
        size: f.size.toInt(),
        mime: mime,
      );
    }
    if (content.hasNotification()) {
      return NotificationBody(
        eventType: content.notification.eventType.isNotEmpty
            ? content.notification.eventType
            : 'system',
        payload: content.notification.payload.isNotEmpty
            ? content.notification.payload
            : null,
      );
    }
    if (content.hasCustom()) {
      return CustomBody(
        customType: content.custom.customType.isNotEmpty
            ? content.custom.customType
            : 'custom',
        payload:
            content.custom.payload.isNotEmpty ? content.custom.payload : null,
      );
    }
    return const NotificationBody(eventType: 'message.unsupported');
  }

  // ─── 上行：model → proto MsgContent ────────────────────

  static pb.MsgContent contentToProto(MessageContent c) {
    final mc = pb.MsgContent();
    switch (c) {
      case TextBody():
        mc.text = pb.TextContent()
          ..text = c.text
          ..atUserIds.addAll(c.atUserIds.map(Ids.toInt64));
      case ImageBody():
        mc.image = pb.ImageContent()
          ..objectKey = c.objectKey
          ..thumbKey = c.thumbKey ?? ''
          ..width = c.width ?? 0
          ..height = c.height ?? 0
          ..size = Int64(c.size ?? 0)
          ..mime = c.mime ?? '';
      case VoiceBody():
        mc.voice = pb.VoiceContent()
          ..objectKey = c.objectKey
          ..durationMs = c.durationMs
          ..size = Int64(c.size ?? 0)
          ..codec = c.codec ?? 'opus';
      case FileBody():
        mc.file = pb.FileContent()
          ..objectKey = c.objectKey
          ..fileName = c.fileName
          ..size = Int64(c.size ?? 0)
          ..mime = c.mime ?? '';
      case VideoBody():
        mc.file = pb.FileContent()
          ..objectKey = c.objectKey
          ..fileName = c.fileName
          ..size = Int64(c.size ?? 0)
          ..mime = c.mime ?? 'video/mp4';
      case NotificationBody():
        mc.notification = pb.NotificationContent()
          ..eventType = c.eventType
          ..payload = c.payload ?? '';
      case CustomBody():
        mc.custom = pb.CustomContent()
          ..customType = c.customType
          ..payload = c.payload ?? '';
    }
    return mc;
  }

  // ─── 上行帧 body 构造 ──────────────────────────────────

  /// 构造 MsgSend body。target 三选一：已有会话用 convId；首次单聊用 toUserId；群用 groupId。
  static Uint8List buildMsgSend({
    required String clientMsgId,
    required MessageContent content,
    String? convId,
    String? toUserId,
    String? groupId,
  }) {
    final msg = pb.MsgSend()
      ..clientMsgId = clientMsgId
      ..content = contentToProto(content);
    if (convId != null && !Ids.isZeroOrEmpty(convId)) {
      msg.convId = Ids.toInt64(convId);
    } else if (groupId != null && !Ids.isZeroOrEmpty(groupId)) {
      msg.groupId = Ids.toInt64(groupId);
    } else if (toUserId != null && !Ids.isZeroOrEmpty(toUserId)) {
      msg.toUserId = Ids.toInt64(toUserId);
    }
    return msg.writeToBuffer();
  }

  static Uint8List buildSyncReq({
    required String convListVersion,
    required List<({String convId, String localMaxSeq})> versions,
  }) {
    final req = pb.SyncReq()..convListVersion = Ids.toInt64(convListVersion);
    for (final v in versions) {
      req.convVersions.add(
        pb.SyncReq_ConvVersion()
          ..convId = Ids.toInt64(v.convId)
          ..localMaxSeq = Ids.toInt64(v.localMaxSeq),
      );
    }
    return req.writeToBuffer();
  }

  static Uint8List buildRecvAck(List<({String convId, String seq})> items) {
    final ack = pb.MsgRecvAck();
    for (final it in items) {
      ack.items.add(
        pb.MsgRecvAck_AckItem()
          ..convId = Ids.toInt64(it.convId)
          ..seq = Ids.toInt64(it.seq),
      );
    }
    return ack.writeToBuffer();
  }

  static Uint8List buildReadReport(String convId, String readSeq) {
    return (pb.ReadReport()
          ..convId = Ids.toInt64(convId)
          ..readSeq = Ids.toInt64(readSeq))
        .writeToBuffer();
  }
}
