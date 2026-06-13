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
import { idToString } from '../utils/id';
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
      handleMsgSendAck(frame.body as Uint8Array);
      break;
    case WsCmd.SYNC_RESP:
      handleSyncResp(frame.body as Uint8Array);
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
  if (ack.code !== 0) {
    useSocketStore.getState().setStatus('error', ack.message || '网关鉴权失败');
    socket.disconnect(false);
    return;
  }
  useSocketStore.getState().setStatus('connected');
  useSocketStore.getState().setHeartbeatInterval(Number(ack.heartbeatIntervalSec || 30));
  socket.resetBackoff();
  socket.startHeartbeat();
  socket.sendSyncReq();
}

function handleKick(socket: ImSocket, body: Uint8Array) {
  const kick = KickNotify.decode(body);
  useUiStore.getState().setKickMessage(kick.message || '当前账号已在其他设备登录');
  useAuthStore.getState().logout();
  socket.disconnect(false);
}

function handleMsgPush(socket: ImSocket, body: Uint8Array, reqId?: Long) {
  const push = MsgPush.decode(body);
  const message = msgPushToChatMessage(push);
  useMessageStore.getState().appendMessages(message.convId, [message]);

  // L1：已有会话保留原 title/avatar，仅首次用 sender 兜底（群聊 title 是群名，不能被发送方覆盖）
  const convId = message.convId;
  const existing = useConvStore.getState().conversations.get(convId);
  useConvStore.getState().upsertConv({
    convId,
    type: Number(push.convType ?? 0),
    title: existing?.title ?? message.sender.nickname,
    avatar: existing?.avatar ?? message.sender.avatar,
    maxSeq: idToString(push.seq),
    readSeq: existing?.readSeq ?? '0',
    pinned: existing?.pinned ?? false,
    muted: existing?.muted ?? false,
    lastMsgAbstract: message.content.kind === 'text' ? message.content.text : '[新消息]',
    lastMsgTime: idToString(push.sendTime),
  });
  socket.sendRecvAck([{ convId, seq: idToString(push.seq) }], reqId);
}

function handleMsgSendAck(body: Uint8Array) {
  const ack = MsgSendAck.decode(body);
  const convId = idToString(ack.convId);
  useMessageStore.getState().updateByClientMsgId(convId, ack.clientMsgId, {
    convId,
    serverMsgId: idToString(ack.serverMsgId),
    seq: idToString(ack.seq),
    sendTime: idToString(ack.serverTime),
    status: ack.code === 0 ? 'sent' : 'failed',
  });
  if (ack.code === 0) {
    const existing = useConvStore.getState().conversations.get(convId);
    useConvStore.getState().upsertConv({
      convId,
      type: existing?.type ?? 1,
      title: existing?.title ?? '新会话',
      avatar: existing?.avatar,
      maxSeq: idToString(ack.seq),
      readSeq: existing?.readSeq ?? '0',
      pinned: existing?.pinned ?? false,
      muted: existing?.muted ?? false,
      lastMsgAbstract: existing?.lastMsgAbstract ?? '',
      lastMsgTime: idToString(ack.serverTime),
    });
  }
}

function handleSyncResp(body: Uint8Array) {
  const resp = SyncResp.decode(body);
  if (resp.fullSync) {
    useMessageStore.getState().clear();
  }
  useConvStore.getState().setConvListVersion(idToString(resp.convListVersion));
  for (const delta of resp.deltas ?? []) {
    if (!delta.conv) continue;
    const conv = convInfoToConversation(delta.conv);
    if ((delta as { deleted?: boolean }).deleted) {
      useConvStore.getState().removeConv(conv.convId);
      continue;
    }
    useConvStore.getState().upsertConv(conv);
    const messages = (delta.msgs ?? []).map(msgPushToChatMessage);
    if (messages.length) {
      useMessageStore.getState().appendMessages(conv.convId, messages);
    }
  }
}

function handleReadNotify(body: Uint8Array) {
  const notify = ReadNotify.decode(body);
  useConvStore.getState().updateReadSeq(idToString(notify.convId), idToString(notify.readSeq));
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
