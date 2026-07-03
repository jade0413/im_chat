import type { MessageItemResponse } from '../api/types';
import { createUuid } from '../config';
import { idToString } from '../utils/id';
import type { ChatMessage, Conversation, MessageContent, SenderInfo } from '../store/types';
import { useUserStore } from '../store/userStore';
import type { im } from '../proto/generated/bundle';

export function convInfoToConversation(conv: im.body.v1.IConvInfo): Conversation {
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
    csStatus: (conv as { csStatus?: string }).csStatus || undefined,
  };
}

export function msgPushToChatMessage(push: im.body.v1.IMsgPush): ChatMessage {
  return {
    clientMsgId: push.clientMsgId || createUuid(),
    serverMsgId: idToString(push.serverMsgId),
    seq: idToString(push.seq),
    convId: idToString(push.convId),
    sender: senderToInfo(push.sender),
    content: protoContentToMessageContent(push.content ?? null),
    sendTime: idToString(push.sendTime),
    status: 'sent',
  };
}

/**
 * 历史消息映射（L2）：优先从 userStore 缓存取昵称/头像，
 * 缓存未命中时用 userId 兜底（MessageList 会随后触发批量拉取刷新）。
 */
export function historyItemToChatMessage(item: MessageItemResponse): ChatMessage {
  const senderId = idToString(item.senderId);
  const cached = useUserStore.getState().getUser(senderId);
  return {
    clientMsgId: item.clientMsgId || `${idToString(item.convId)}:${idToString(item.seq)}`,
    serverMsgId: idToString(item.serverMsgId),
    seq: idToString(item.seq),
    convId: idToString(item.convId),
    sender: {
      userId: senderId,
      nickname: cached?.nickname ?? `用户 ${senderId}`,
      avatar: cached?.avatar,
    },
    content:
      item.status === 2
        ? { kind: 'notification', eventType: 'message.revoked' }
        : historyContent(item),
    sendTime: idToString(item.sendTime),
    status: item.status === 2 ? 'revoked' : 'sent',
  };
}

/**
 * 将历史消息 REST 响应转为 MessageContent。
 * 后端直接从 MsgContent 拆出富媒体字段，历史页与实时推送使用同一套渲染模型。
 */
function historyContent(item: MessageItemResponse): import('../store/types').MessageContent {
  if (item.objectKey) {
    const mime = item.mime ?? '';
    if (mime.startsWith('image/')) {
      return {
        kind: 'image',
        objectKey: item.objectKey,
        thumbKey: item.thumbKey,
        width: Number(item.width ?? 0),
        height: Number(item.height ?? 0),
        size: Number(item.size ?? 0),
        mime,
      };
    }
    if (mime.startsWith('video/')) {
      return {
        kind: 'video',
        objectKey: item.objectKey,
        fileName: item.fileName ?? '未命名视频',
        thumbKey: item.thumbKey,
        durationMs: item.durationMs === undefined ? undefined : Number(item.durationMs),
        size: Number(item.size ?? 0),
        mime,
      };
    }
    if (mime.startsWith('audio/') || item.msgType === 3) {
      return {
        kind: 'voice',
        objectKey: item.objectKey,
        durationMs: Number(item.durationMs ?? 0),
        size: Number(item.size ?? 0),
        codec: item.codec,
      };
    }
    return {
      kind: 'file',
      objectKey: item.objectKey,
      fileName: item.fileName ?? '未命名文件',
      size: Number(item.size ?? 0),
      mime,
    };
  }
  switch (item.msgType) {
    case 1: return { kind: 'text', text: item.text || '' };
    case 2: return { kind: 'notification', eventType: 'message.unsupported', payload: '图片消息（请在手机端查看）' };
    case 3: return { kind: 'notification', eventType: 'message.unsupported', payload: '语音消息（请在手机端查看）' };
    case 4: return { kind: 'notification', eventType: 'message.unsupported', payload: '文件消息（请在手机端查看）' };
    default: return { kind: 'text', text: item.text || '' };
  }
}

export function protoContentToMessageContent(
  content: im.common.v1.IMsgContent | null,
): MessageContent {
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
    if (mime?.startsWith('video/')) {
      return {
        kind: 'video',
        objectKey: content.file.objectKey ?? '',
        fileName: content.file.fileName ?? '未命名视频',
        size: Number(content.file.size ?? 0),
        mime,
        thumbKey: content.file.thumbKey || undefined,
        durationMs: content.file.durationMs ? Number(content.file.durationMs) : undefined,
      };
    }
    return {
      kind: 'file',
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

function senderToInfo(sender: im.body.v1.ISender | null | undefined): SenderInfo {
  return {
    userId: idToString(sender?.userId),
    nickname: sender?.nickname || `用户 ${idToString(sender?.userId)}`,
    avatar: sender?.avatar || undefined,
    verifiedType: Number(sender?.verifiedType ?? 0),
    userType: Number(sender?.userType ?? 0),
  };
}
