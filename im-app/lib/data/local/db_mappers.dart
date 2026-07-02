import 'package:drift/drift.dart';

import '../models/chat_message.dart';
import '../models/conversation.dart';
import '../models/enums.dart';
import '../models/sender_info.dart';
import 'app_database.dart';
import 'content_json.dart';

/// drift 行 ↔ 领域模型 互转。
extension ConversationRowX on ConversationRow {
  Conversation toModel() => Conversation(
        convId: convId,
        type: type,
        title: title,
        avatar: avatar,
        peerUserId: peerUserId,
        groupId: groupId,
        maxSeq: maxSeq,
        syncSeq: syncSeq,
        readSeq: readSeq,
        peerReadSeq: peerReadSeq,
        pinned: pinned,
        muted: muted,
        lastMsgAbstract: lastMsgAbstract,
        lastMsgTime: lastMsgTimeMs?.toString(),
        csStatus: csStatus,
        draft: draft,
      );
}

extension ConversationModelX on Conversation {
  ConversationsCompanion toCompanion() => ConversationsCompanion(
        convId: Value(convId),
        type: Value(type),
        title: Value(title),
        avatar: Value(avatar),
        peerUserId: Value(peerUserId),
        groupId: Value(groupId),
        maxSeq: Value(maxSeq),
        syncSeq: Value(syncSeq),
        readSeq: Value(readSeq),
        peerReadSeq: Value(peerReadSeq),
        pinned: Value(pinned),
        muted: Value(muted),
        lastMsgAbstract: Value(lastMsgAbstract),
        lastMsgTimeMs: Value(int.tryParse(lastMsgTime ?? '')),
        csStatus: Value(csStatus),
        draft: Value(draft),
      );
}

extension MessageRowX on MessageRow {
  ChatMessage toModel() => ChatMessage(
        clientMsgId: clientMsgId,
        convId: convId,
        serverMsgId: serverMsgId,
        seq: seq,
        sender: SenderInfo(
          userId: senderId,
          nickname: senderNickname,
          avatar: senderAvatar,
          verifiedType: senderVerifiedType,
          userType: senderUserType,
        ),
        content: ContentJson.decode(contentJson),
        sendTime: sendTimeMs.toString(),
        status: MessageStatus
            .values[status.clamp(0, MessageStatus.values.length - 1)],
        failCode: failCode,
      );
}

extension MessageModelX on ChatMessage {
  MessagesCompanion toCompanion() => MessagesCompanion(
        clientMsgId: Value(clientMsgId),
        convId: Value(convId),
        serverMsgId: Value(serverMsgId),
        seq: Value(seq),
        seqInt: Value(seq == null ? null : int.tryParse(seq!)),
        senderId: Value(sender.userId),
        senderNickname: Value(sender.nickname),
        senderAvatar: Value(sender.avatar),
        senderVerifiedType: Value(sender.verifiedType),
        senderUserType: Value(sender.userType),
        contentJson: Value(ContentJson.encode(content)),
        sendTimeMs: Value(int.tryParse(sendTime) ?? 0),
        status: Value(status.index),
        failCode: Value(failCode),
      );
}
