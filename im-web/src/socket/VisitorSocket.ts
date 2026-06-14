import Long from 'long';
import { APP_VERSION, createUuid, PLATFORM_WEB, TENANT_ID, WS_URL } from '../config';
import {
  AuthReq,
  AuthResp,
  decodeFrame,
  encodeFrame,
  ErrorBody,
  MsgContent,
  MsgPush,
  MsgRecvAck,
  MsgSend,
  MsgSendAck,
  ReadReport,
  SyncReq,
  SyncResp,
  WsCmd,
} from '../proto/codec';
import type { ChatMessage, ConnectionState } from '../store/types';
import type { im } from '../proto/generated/bundle';
import { compareIdLike, idToLong, idToString } from '../utils/id';
import { protoContentToMessageContent } from './mappers';

export interface VisitorSocketSession {
  accessToken: string;
  conversationId: string;
  visitorId: string;
  displayName: string;
  visitorToken: string;
}

interface VisitorSocketEvents {
  onStatus: (state: ConnectionState, message?: string) => void;
  onMessage: (message: ChatMessage) => void;
  onAck: (clientMsgId: string, patch: Partial<ChatMessage>) => void;
  onError: (message: string) => void;
}

export class VisitorSocket {
  private ws: WebSocket | null = null;
  private heartbeatTimer: number | null = null;
  private session: VisitorSocketSession | null = null;
  private localMaxSeq = '0';

  constructor(private readonly events: VisitorSocketEvents) {}

  connect(session: VisitorSocketSession) {
    this.disconnect();
    this.session = session;
    this.localMaxSeq = '0';
    this.events.onStatus('connecting', '正在连接客服');

    const ws = new WebSocket(WS_URL);
    this.ws = ws;
    ws.binaryType = 'arraybuffer';

    ws.onopen = () => {
      const body = AuthReq.encode(
        AuthReq.create({
          token: session.accessToken,
          tenantId: Long.fromNumber(TENANT_ID),
          deviceId: `visitor_${session.visitorToken}`,
          platform: PLATFORM_WEB,
          appVersion: APP_VERSION,
          timestamp: Long.fromNumber(Math.floor(Date.now() / 1000)),
        }),
      ).finish();
      this.sendRaw(WsCmd.AUTH, body);
    };

    ws.onmessage = (event) => {
      try {
        const frame = decodeVisitorFrame(event.data);
        this.dispatch(frame.cmd ?? 0, frame.body as Uint8Array, normalizeReqId(frame.reqId));
      } catch (error) {
        this.events.onError(error instanceof Error ? error.message : String(error));
      }
    };

    ws.onerror = () => {
      this.events.onStatus('error', '客服连接异常');
    };

    ws.onclose = () => {
      this.stopHeartbeat();
      if (this.ws === ws) {
        this.ws = null;
      }
      this.events.onStatus('closed', '客服连接已断开');
    };
  }

  disconnect() {
    this.stopHeartbeat();
    if (this.ws && this.ws.readyState !== WebSocket.CLOSED) {
      this.ws.onopen = null;
      this.ws.onmessage = null;
      this.ws.onerror = null;
      this.ws.onclose = null;
      this.ws.close();
    }
    this.ws = null;
  }

  sendText(text: string): ChatMessage | null {
    const session = this.session;
    if (!session) {
      this.events.onError('游客会话尚未初始化');
      return null;
    }
    const clientMsgId = createUuid();
    const optimistic: ChatMessage = {
      clientMsgId,
      convId: session.conversationId,
      sender: {
        userId: session.visitorId,
        nickname: session.displayName,
        userType: 3,
      },
      content: { kind: 'text', text },
      sendTime: String(Date.now()),
      status: 'sending',
    };

    const body = MsgSend.encode(
      MsgSend.create({
        clientMsgId,
        convId: idToLong(session.conversationId),
        content: MsgContent.create({ text: { text, atUserIds: [] } }),
      }),
    ).finish();

    if (!this.sendRaw(WsCmd.MSG_SEND, body)) {
      return { ...optimistic, status: 'failed' };
    }
    return optimistic;
  }

  private dispatch(cmd: number, body: Uint8Array, reqId?: Long) {
    switch (cmd) {
      case WsCmd.AUTH_ACK:
        this.handleAuthAck(body);
        break;
      case WsCmd.PONG:
        this.events.onStatus('connected', '客服已连接');
        break;
      case WsCmd.MSG_PUSH:
        this.handleMsgPush(body, reqId);
        break;
      case WsCmd.MSG_SEND_ACK:
        this.handleMsgSendAck(body);
        break;
      case WsCmd.SYNC_RESP:
        this.handleSyncResp(body);
        break;
      case WsCmd.ERROR:
        this.handleError(body);
        break;
      default:
        break;
    }
  }

  private handleAuthAck(body: Uint8Array) {
    const ack = AuthResp.decode(body);
    if (ack.code !== 0) {
      this.events.onStatus('error', ack.message || '游客鉴权失败');
      this.events.onError(ack.message || '游客鉴权失败');
      this.disconnect();
      return;
    }
    this.events.onStatus('connected', '客服已连接');
    this.startHeartbeat(Number(ack.heartbeatIntervalSec || 30));
    this.sendSyncReq();
  }

  private handleMsgPush(body: Uint8Array, reqId?: Long) {
    const push = MsgPush.decode(body);
    const message = this.pushToMessage(push);
    if (message.seq && compareIdLike(message.seq, this.localMaxSeq) > 0) {
      this.localMaxSeq = message.seq;
    }
    this.events.onMessage(message);
    if (message.seq) {
      this.sendRecvAck([{ convId: message.convId, seq: message.seq }], reqId);
      this.sendReadReport();
    }
  }

  private handleMsgSendAck(body: Uint8Array) {
    const ack = MsgSendAck.decode(body);
    const patch: Partial<ChatMessage> = {
      convId: idToString(ack.convId),
      serverMsgId: idToString(ack.serverMsgId),
      seq: idToString(ack.seq),
      sendTime: idToString(ack.serverTime),
      status: ack.code === 0 ? 'sent' : 'failed',
    };
    if (patch.seq && compareIdLike(patch.seq, this.localMaxSeq) > 0) {
      this.localMaxSeq = patch.seq;
    }
    this.events.onAck(ack.clientMsgId, patch);
    if (ack.code !== 0) {
      this.events.onError('消息发送失败');
    }
  }

  private handleSyncResp(body: Uint8Array) {
    const resp = SyncResp.decode(body);
    for (const delta of resp.deltas ?? []) {
      for (const push of delta.msgs ?? []) {
        const message = this.pushToMessage(push);
        if (message.seq && compareIdLike(message.seq, this.localMaxSeq) > 0) {
          this.localMaxSeq = message.seq;
        }
        this.events.onMessage(message);
      }
    }
    this.sendReadReport();
  }

  private handleError(body: Uint8Array) {
    const error = ErrorBody.decode(body);
    this.events.onError(error.message || `请求失败：${error.code}`);
  }

  private sendSyncReq() {
    const session = this.session;
    if (!session) return;
    const body = SyncReq.encode(
      SyncReq.create({
        convListVersion: Long.ZERO,
        convVersions: [
          {
            convId: idToLong(session.conversationId),
            localMaxSeq: idToLong(this.localMaxSeq),
          },
        ],
      }),
    ).finish();
    this.sendRaw(WsCmd.SYNC_REQ, body);
  }

  private sendRecvAck(items: { convId: string; seq: string }[], reqId?: Long) {
    const body = MsgRecvAck.encode(
      MsgRecvAck.create({
        items: items.map((item) => ({
          convId: idToLong(item.convId),
          seq: idToLong(item.seq),
        })),
      }),
    ).finish();
    this.sendRaw(WsCmd.MSG_RECV_ACK, body, reqId);
  }

  private sendReadReport() {
    const session = this.session;
    if (!session || this.localMaxSeq === '0') {
      return;
    }
    const body = ReadReport.encode(
      ReadReport.create({
        convId: idToLong(session.conversationId),
        readSeq: idToLong(this.localMaxSeq),
      }),
    ).finish();
    this.sendRaw(WsCmd.READ_REPORT, body);
  }

  private sendRaw(cmd: number, body?: Uint8Array, reqId?: Long) {
    if (this.ws?.readyState !== WebSocket.OPEN) {
      return false;
    }
    this.ws.send(encodeFrame(cmd, body, reqId));
    return true;
  }

  private startHeartbeat(intervalSec: number) {
    this.stopHeartbeat();
    this.heartbeatTimer = window.setInterval(() => {
      this.sendRaw(WsCmd.PING);
    }, Math.max(10, intervalSec) * 1000);
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      window.clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private pushToMessage(push: im.body.v1.IMsgPush): ChatMessage {
    const senderId = idToString(push.sender?.userId);
    const senderName = push.sender?.nickname || (senderId === this.session?.visitorId ? this.session.displayName : '坐席');
    return {
      clientMsgId: push.clientMsgId || `${idToString(push.convId)}:${idToString(push.seq)}`,
      serverMsgId: idToString(push.serverMsgId),
      seq: idToString(push.seq),
      convId: idToString(push.convId),
      sender: {
        userId: senderId,
        nickname: senderName,
        avatar: push.sender?.avatar || undefined,
        verifiedType: Number(push.sender?.verifiedType ?? 0),
        userType: Number(push.sender?.userType ?? 0),
      },
      content: protoContentToMessageContent(push.content ?? null),
      sendTime: idToString(push.sendTime),
      status: 'sent',
    };
  }
}

function decodeVisitorFrame(data: unknown) {
  if (data instanceof ArrayBuffer) {
    return decodeFrame(data);
  }
  throw new Error('不支持的 WebSocket 数据格式');
}

function normalizeReqId(reqId: unknown): Long | undefined {
  if (reqId == null) {
    return undefined;
  }
  return Long.isLong(reqId) ? reqId : Long.fromValue(reqId as Long | number | string);
}
