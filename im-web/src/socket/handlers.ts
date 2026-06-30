import Long from 'long';
import {
  AuthResp,
  ConvNotify,
  ErrorBody,
  KickNotify,
  MsgPush,
  MsgRecvAck,
  MsgSendAck,
  ReadNotify,
  RevokeNotify,
  SyncResp,
  WsCmd,
} from '../proto/codec';
import type { im } from '../proto/generated/bundle';
import { useAuthStore } from '../store/authStore';
import { useConvStore } from '../store/convStore';
import { useMessageStore } from '../store/messageStore';
import { useSocketStore } from '../store/socketStore';
import { useUiStore } from '../store/uiStore';
import { useUserStore } from '../store/userStore';
import { compareIdLike, idToString } from '../utils/id';
import { contiguousSeqFromMessages, hasSeqGap, maxSeq, resolveLocalSyncSeq } from '../utils/seq';
import { convInfoToConversation, msgPushToChatMessage } from './mappers';
import type { ImSocket } from './ImSocket';

export function dispatchFrame(socket: ImSocket, frame: im.ws.v1.IFrame) {
  switch (frame.cmd) {
    case WsCmd.AUTH_ACK:
      handleAuthAck(socket, frame.body as Uint8Array);
      break;
    case WsCmd.PONG:
      useSocketStore.getState().setStatus('connected');
      break;
    case WsCmd.KICK:
      handleKick(socket, frame.body as Uint8Array);
      break;
    case WsCmd.MSG_PUSH:
      handleMsgPush(socket, frame.body as Uint8Array, frame.reqId as Long | undefined);
      break;
    case WsCmd.MSG_SEND_ACK:
      handleMsgSendAck(socket, frame.body as Uint8Array);
      break;
    case WsCmd.SYNC_RESP:
      handleSyncResp(socket, frame.body as Uint8Array);
      break;
    case WsCmd.READ_NOTIFY:
      handleReadNotify(frame.body as Uint8Array);
      break;
    case WsCmd.REVOKE_NOTIFY:
      handleRevokeNotify(frame.body as Uint8Array);
      break;
    case WsCmd.CONV_NOTIFY:
      handleConvNotify(frame.body as Uint8Array);
      break;
    case WsCmd.ERROR:
      handleError(frame.body as Uint8Array);
      break;
    default:
      console.debug('[im-web] unhandled ws cmd', frame.cmd);
  }
}

function handleAuthAck(socket: ImSocket, body: Uint8Array) {
  const ack = AuthResp.decode(body);
  console.info('[im-web/ws] AUTH_ACK received', {
    code: ack.code,
    message: ack.message,
    userId: ack.userId?.toString?.() ?? ack.userId,
    heartbeatIntervalSec: ack.heartbeatIntervalSec,
  });
  if (ack.code !== 0) {
    useSocketStore.getState().setStatus('error', ack.message || '网关鉴权失败');
    useSocketStore.getState().setEvent('AUTH_ACK 失败', ack.message || '网关鉴权失败');
    // 不能直接 disconnect(false)，否则只会显示"网络连接错误"而非正确处理被踢场景。
    // handleAuthAckFailure 会：
    //   1. 先尝试 refresh token（处理正常 token 过期）
    //   2. refresh 401/403 → 真正被踢，显示下线弹窗
    //   3. refresh 网络错误 → 稍后重连
    //   4. 同一连接生命周期内只允许刷新一次，防止双设备互踢死循环
    void socket.handleAuthAckFailure(ack.message || '登录状态已失效，请重新登录');
    return;
  }
  useSocketStore.getState().setStatus('connected');
  useSocketStore.getState().setEvent(`AUTH_ACK 成功 user=${ack.userId?.toString?.() ?? ack.userId}`);
  useSocketStore.getState().setHeartbeatInterval(Number(ack.heartbeatIntervalSec || 30));
  socket.resetBackoff();
  socket.markAuthReady();
  socket.startHeartbeat();
  socket.sendSyncReq();
  // 重连后补发所有因断网滞留的 pending 消息
  socket.drainPending();
}

function handleKick(socket: ImSocket, body: Uint8Array) {
  const kick = KickNotify.decode(body);
  useSocketStore.getState().setEvent('收到 KICK', kick.message || '当前账号已在其他设备登录');

  // 网关按 conn_id 定向下发 KICK；当前连接收到 KICK 就应停止重连并提示用户。
  useUiStore.getState().setKickMessage(kick.message || '当前账号已在其他设备登录');
  useAuthStore.getState().logout();
  // manualClose=true：被踢是有意断开，不启动重连逻辑
  socket.disconnect(true);
}

function handleMsgPush(socket: ImSocket, body: Uint8Array, reqId?: Long) {
  const push = MsgPush.decode(body);
  const message = msgPushToChatMessage(push);
  const convId = message.convId;
  const existing = useConvStore.getState().conversations.get(convId);
  const pushSeq = idToString(push.seq);
  const currentSyncSeq = currentLocalSeq(convId, existing?.syncSeq);
  const hasGap = hasSeqGap(currentSyncSeq, pushSeq);
  useMessageStore.getState().appendMessages(convId, [message]);
  const nextSyncSeq = contiguousSeqFromMessages(useMessageStore.getState().messages.get(convId), currentSyncSeq);
  if (hasGap) {
    socket.sendSyncReq();
  }
  // 页面在后台时弹出浏览器通知
  maybeNotify(message.sender.nickname, message.content);

  // L1：已有会话保留原 title/avatar/groupId/peerUserId，仅首次用 sender 兜底
  useConvStore.getState().upsertConv({
    convId,
    type: Number(push.convType ?? 0),
    title: existing?.title ?? message.sender.nickname,
    avatar: existing?.avatar ?? message.sender.avatar,
    peerUserId: existing?.peerUserId,
    groupId: existing?.groupId,
    maxSeq: maxSeq(existing?.maxSeq, pushSeq),
    syncSeq: nextSyncSeq,
    readSeq: existing?.readSeq ?? '0',
    pinned: existing?.pinned ?? false,
    muted: existing?.muted ?? false,
    lastMsgAbstract: msgAbstract(message.content),
    lastMsgTime: idToString(push.sendTime),
  });
  socket.sendRecvAck([{ convId, seq: idToString(push.seq) }], reqId);
}

function handleMsgSendAck(socket: ImSocket, body: Uint8Array) {
  const ack = MsgSendAck.decode(body);
  // 收到 ACK 后移除 pending 记录，无论成功还是服务端拒绝都不再重发
  const pendingConvId = socket.dequeue(ack.clientMsgId);
  const ackConvId = idToString(ack.convId);
  const convId = ackConvId !== '0' ? ackConvId : pendingConvId ?? ackConvId;
  if (ack.code !== 0) {
    useMessageStore.getState().updateByClientMsgId(convId, ack.clientMsgId, {
      status: 'failed',
      // 失败时存下服务端 code，供 UI 展示具体原因（2002=需先加好友、2001=被对方拉黑）
      failCode: ack.code,
    });
    return;
  }
  const seq = idToString(ack.seq);
  const existing = useConvStore.getState().conversations.get(convId);
  const currentSyncSeq = currentLocalSeq(convId, existing?.syncSeq);
  useMessageStore.getState().updateByClientMsgId(convId, ack.clientMsgId, {
    convId,
    serverMsgId: idToString(ack.serverMsgId),
    seq,
    status: 'sent',
    failCode: undefined,
  });
  const nextSyncSeq = contiguousSeqFromMessages(useMessageStore.getState().messages.get(convId), currentSyncSeq);
  useConvStore.getState().upsertConv({
    convId,
    type: existing?.type ?? 1,
    title: existing?.title ?? '新会话',
    avatar: existing?.avatar,
    peerUserId: existing?.peerUserId,
    groupId: existing?.groupId,
    maxSeq: maxSeq(existing?.maxSeq, seq),
    syncSeq: nextSyncSeq,
    // Bug1 fix: 自己发的消息，readSeq 同步更新为最新 seq，避免产生未读红点
    readSeq: seq,
    pinned: existing?.pinned ?? false,
    muted: existing?.muted ?? false,
    lastMsgAbstract: existing?.lastMsgAbstract ?? '',
    lastMsgTime: idToString(ack.serverTime),
  });
}

function handleSyncResp(socket: ImSocket, body: Uint8Array) {
  const resp = SyncResp.decode(body);
  console.debug('[SYNC] fullSync=%s deltas=%d convListVersion=%s',
    resp.fullSync, resp.deltas?.length ?? 0, idToString(resp.convListVersion));
  if (resp.fullSync) {
    useMessageStore.getState().clear();
    for (const conv of useConvStore.getState().conversations.values()) {
      useConvStore.getState().upsertConv({ ...conv, syncSeq: '0' });
    }
  }
  useConvStore.getState().setConvListVersion(idToString(resp.convListVersion));
  let shouldContinueSync = false;
  for (const delta of resp.deltas ?? []) {
    if (!delta.conv) continue;
    const conv = convInfoToConversation(delta.conv);
    console.debug('[SYNC] delta convId=%s msgs=%d serverMaxSeq=%s lastMsgAbstract=%s',
      conv.convId, delta.msgs?.length ?? 0, idToString(delta.serverMaxSeq), delta.conv.lastMsgAbstract);
    if (delta.conv.deleted) {
      useConvStore.getState().removeConv(conv.convId);
      continue;
    }
    const beforeSyncSeq = currentLocalSeq(conv.convId, useConvStore.getState().conversations.get(conv.convId)?.syncSeq);
    const messages = (delta.msgs ?? []).map(msgPushToChatMessage);
    if (messages.length) {
      useMessageStore.getState().appendMessages(conv.convId, messages);
    }
    const nextSyncSeq = syncSeqAfterDelta(
      beforeSyncSeq,
      useMessageStore.getState().messages.get(conv.convId),
      idToString(delta.serverMaxSeq),
      Boolean(delta.hasMore),
    );
    useConvStore.getState().upsertConv({ ...conv, syncSeq: nextSyncSeq });
    useMessageStore.getState().setHasMore(conv.convId, Boolean(delta.hasMore));
    shouldContinueSync = shouldContinueSync || (Boolean(delta.hasMore) && compareIdLike(nextSyncSeq, beforeSyncSeq) > 0);
  }
  if (shouldContinueSync) {
    window.setTimeout(() => socket.sendSyncReq(), 0);
  }

  // C2C 会话的 title 由后端填为 peerUserId 字符串，批量预加载昵称
  // 加载完成后 ConvItem/ChatHeader 从 userStore 订阅取值，自动刷新
  const peerIds = Array.from(useConvStore.getState().conversations.values())
    .filter((c) => c.type === 1 && c.peerUserId)
    .map((c) => c.peerUserId as string);
  if (peerIds.length > 0) {
    void useUserStore.getState().ensureUsers(peerIds);
  }
}

function currentLocalSeq(convId: string, storedSyncSeq?: string): string {
  return resolveLocalSyncSeq(storedSyncSeq, useMessageStore.getState().messages.get(convId));
}

function syncSeqAfterDelta(
  currentSeq: string,
  messages: Iterable<{ seq?: string | null }> | undefined,
  serverMaxSeq: string,
  hasMore: boolean,
): string {
  const syncSeq = contiguousSeqFromMessages(messages, currentSeq);
  if (!hasMore && compareIdLike(serverMaxSeq, syncSeq) > 0 && !messages) {
    return serverMaxSeq;
  }
  return syncSeq;
}

function handleReadNotify(body: Uint8Array) {
  const notify = ReadNotify.decode(body);
  const currentUserId = idToString(useAuthStore.getState().user?.id);
  if (idToString(notify.readerUserId) === currentUserId) {
    return;
  }
  // READ_NOTIFY 来自对端的已读上报，记录对端已读位置（用于已读回执 UI）
  // 本端自己的已读位置由 markRead 维护，不在此处更新，避免混淆
  useConvStore.getState().updatePeerReadSeq(idToString(notify.convId), idToString(notify.readSeq));
}

function handleRevokeNotify(body: Uint8Array) {
  const notify = RevokeNotify.decode(body);
  useMessageStore.getState().revokeMessage(idToString(notify.convId), idToString(notify.seq));
}

function handleConvNotify(body: Uint8Array) {
  const notify = ConvNotify.decode(body);
  if (!notify.conv) return;
  const conv = convInfoToConversation(notify.conv);
  if (notify.changeType === 'removed') {
    useConvStore.getState().removeConv(conv.convId);
  } else {
    useConvStore.getState().upsertConv(conv);
  }
}

function handleError(body: Uint8Array) {
  const error = ErrorBody.decode(body);
  useSocketStore.getState().setStatus('error', error.message || `请求失败：${error.code}`);
  useSocketStore.getState().setEvent('收到 ERROR', error.message || `请求失败：${error.code}`);
}

/** 生成会话列表摘要文字 */
function msgAbstract(content: import('../store/types').MessageContent): string {
  switch (content.kind) {
    case 'text': return content.text.slice(0, 40);
    case 'image': return '[图片]';
    case 'voice': return '[语音]';
    case 'file': return `[文件] ${content.fileName}`;
    case 'video': return '[视频]';
    case 'custom': return `[${content.customType}]`;
    default: return '[消息]';
  }
}

/** 页面隐藏时弹出浏览器通知；首次调用自动请求权限 */
function maybeNotify(senderName: string, content: import('../store/types').MessageContent) {
  if (document.visibilityState === 'visible') return;
  if (!('Notification' in window)) return;
  const show = () => {
    let body = '';
    if (content.kind === 'text') body = content.text.slice(0, 80);
    else if (content.kind === 'image') body = '[图片]';
    else if (content.kind === 'voice') body = '[语音]';
    else if (content.kind === 'file' || content.kind === 'video') body = `[文件] ${(content as { fileName?: string }).fileName ?? ''}`;
    else return; // 系统消息不通知
    new Notification(senderName, { body, icon: '/favicon.ico', tag: 'im-chat' });
  };
  if (Notification.permission === 'granted') {
    show();
  } else if (Notification.permission !== 'denied') {
    void Notification.requestPermission().then((p) => { if (p === 'granted') show(); });
  }
}

export function buildRecvAck(items: { convId: string; seq: string }[]) {
  return MsgRecvAck.encode(
    MsgRecvAck.create({
      items: items.map((item) => ({
        convId: Long.fromString(item.convId, false),
        seq: Long.fromString(item.seq, false),
      })),
    }),
  ).finish();
}
