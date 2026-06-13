import type { MessageItemResponse } from '../api/types';
import { idToString } from '../utils/id';
import type { ChatMessage, Conversation, MessageContent, SenderInfo } from '../store/types';

export function convInfoToConversation(conv: any): Conversation {
  return {
    convId: idToString(conv.convId),
    type: Number(conv.type ?? 0),
    title: conv.title || '未命名会话',
    avatar: conv.avatar || undefined,
    peerUserId: conv.peerUserId ? idToString(conv.peerUserId) : undefined,
    groupId: conv.groupId ? idToString(conv.groupId) : undefined,
    maxSeq: idToString(conv.maxSeq),
    readSeq: idToString(conv.readSeq),
    pinned: Boolean(conv.pinned),
    muted: Boolean(conv.muted),
    lastMsgAbstract: conv.lastMsgAbstract || '',
    lastMsgTime: conv.lastMsgTime ? idToString(conv.lastMsgTime) : undefined,
    csStatus: conv.csStatus || undefined,
  };
}

export function msgPushToChatMessage(push: any): ChatMessage {
  return {
    clientMsgId: push.clientMsgId || crypto.randomUUID(),
    serverMsgId: idToString(push.serverMsgId),
    seq: idToString(push.seq),
    convId: idToString(push.convId),
    sender: senderToInfo(push.sender),
    content: protoContentToMessageContent(push.content),
    sendTime: idToString(push.sendTime),
    status: 'sent',
  };
}

export function historyItemToChatMessage(item: MessageItemResponse): ChatMessage {
  return {
    clientMsgId: item.clientMsgId || `${idToString(item.convId)}:${idToString(item.seq)}`,
    serverMsgId: idToString(item.serverMsgId),
    seq: idToString(item.seq),
    convId: idToString(item.convId),
    sender: {
      userId: idToString(item.senderId),
      nickname: `用户 ${idToString(item.senderId)}`,
    },
    content:
      item.status === 2
        ? { kind: 'notification', eventType: 'message.revoked' }
        : { kind: 'text', text: item.text || '[暂不支持的历史消息]' },
    sendTime: idToString(item.sendTime),
    status: item.status === 2 ? 'revoked' : 'sent',
  };
}

export function protoContentToMessageContent(content: any): MessageContent {
  if (!content) {
    return { kind: 'notification', eventType: 'message.empty' };
  }
  if (content.text) {
    return { kind: 'text', text: content.text.text ?? '' };
  }
  if (content.image) {
    return {
      kind: 'image',
      objectKey: content.image.objectKey ?? '',
      thumbKey: content.image.thumbKey || undefined,
      width: Number(content.image.width ?? 0),
      height: Number(content.image.height ?? 0),
      size: Number(content.image.size ?? 0),
      mime: content.image.mime || undefined,
    };
  }
  if (content.voice) {
    return {
      kind: 'voice',
      objectKey: content.voice.objectKey ?? '',
      durationMs: Number(content.voice.durationMs ?? 0),
      size: Number(content.voice.size ?? 0),
      codec: content.voice.codec || undefined,
    };
  }
  if (content.file) {
    const mime = content.file.mime || undefined;
    return {
      kind: mime?.startsWith('video/') ? 'video' : 'file',
      objectKey: content.file.objectKey ?? '',
      fileName: content.file.fileName ?? '未命名文件',
      size: Number(content.file.size ?? 0),
      mime,
    };
  }
  if (content.notification) {
    return {
      kind: 'notification',
      eventType: content.notification.eventType || 'system',
      payload: content.notification.payload || undefined,
    };
  }
  if (content.custom) {
    return {
      kind: 'custom',
      customType: content.custom.customType || 'custom',
      payload: content.custom.payload || undefined,
    };
  }
  return { kind: 'notification', eventType: 'message.unsupported' };
}

function senderToInfo(sender: any): SenderInfo {
  return {
    userId: idToString(sender?.userId),
    nickname: sender?.nickname || `用户 ${idToString(sender?.userId)}`,
    avatar: sender?.avatar || undefined,
    verifiedType: Number(sender?.verifiedType ?? 0),
    userType: Number(sender?.userType ?? 0),
  };
}
