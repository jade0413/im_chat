import Long from 'long';
import { APP_VERSION, getOrCreateDeviceId, PLATFORM_WEB, TENANT_ID, WS_URL } from '../config';
import { AuthReq, decodeFrame, encodeFrame, MsgContent, MsgSend, ReadReport, SyncReq, WsCmd } from '../proto/codec';
import { useAuthStore } from '../store/authStore';
import { useConvStore } from '../store/convStore';
import { useMessageStore } from '../store/messageStore';
import { useSocketStore } from '../store/socketStore';
import { idToLong } from '../utils/id';
import { dispatchFrame, buildRecvAck } from './handlers';
import { ReconnectBackoff } from './reconnect';

export class ImSocket {
  private ws: WebSocket | null = null;
  private reconnectTimer: number | null = null;
  private heartbeatTimer: number | null = null;
  private manualClose = false;
  private readonly backoff = new ReconnectBackoff();

  connect(token: string) {
    this.manualClose = false;
    this.clearReconnectTimer();
    this.closeSocket();
    useSocketStore.getState().setStatus('connecting');

    this.ws = new WebSocket(WS_URL);
    this.ws.binaryType = 'arraybuffer';

    this.ws.onopen = () => {
      const body = AuthReq.encode(
        AuthReq.create({
          token,
          tenantId: Long.fromNumber(TENANT_ID),
          deviceId: getOrCreateDeviceId(),
          platform: PLATFORM_WEB,
          appVersion: APP_VERSION,
          timestamp: Long.fromNumber(Math.floor(Date.now() / 1000)),
        }),
      ).finish();
      this.sendRaw(WsCmd.AUTH, body);
    };

    this.ws.onmessage = (event) => {
      try {
        const frame = decodeWsFrame(event.data);
        dispatchFrame(this, frame);
      } catch (error) {
        console.error('[im-web] ws frame decode failed', error);
      }
    };

    this.ws.onerror = () => {
      useSocketStore.getState().setStatus('error', 'WebSocket 连接异常');
    };

    this.ws.onclose = () => {
      this.stopHeartbeat();
      this.ws = null;
      if (!this.manualClose && useAuthStore.getState().accessToken) {
        this.scheduleReconnect();
      } else {
        useSocketStore.getState().setStatus('closed');
      }
    };
  }

  disconnect(manual = true) {
    this.manualClose = manual;
    this.clearReconnectTimer();
    this.stopHeartbeat();
    this.closeSocket();
  }

  resetBackoff() {
    this.backoff.reset();
  }

  startHeartbeat() {
    this.stopHeartbeat();
    const interval = Math.max(10, useSocketStore.getState().heartbeatIntervalSec) * 1000;
    this.heartbeatTimer = window.setInterval(() => this.sendRaw(WsCmd.PING), interval);
  }

  sendSyncReq() {
    const convState = useConvStore.getState();
    const body = SyncReq.encode(
      SyncReq.create({
        convListVersion: idToLong(convState.convListVersion),
        convVersions: Array.from(convState.conversations.values()).map((conv) => ({
          convId: idToLong(conv.convId),
          localMaxSeq: idToLong(conv.maxSeq),
        })),
      }),
    ).finish();
    this.sendRaw(WsCmd.SYNC_REQ, body);
  }

  sendText(convId: string, text: string) {
    const auth = useAuthStore.getState();
    const clientMsgId = crypto.randomUUID();
    const now = String(Date.now());
    useMessageStore.getState().addOptimistic({
      clientMsgId,
      convId,
      sender: {
        userId: String(auth.user?.id ?? '0'),
        nickname: auth.user?.nickname || auth.user?.account || '我',
        avatar: auth.user?.avatar,
      },
      content: { kind: 'text', text },
      sendTime: now,
      status: 'sending',
    });
    const content = MsgContent.create({ text: { text, atUserIds: [] } });
    const body = MsgSend.encode(
      MsgSend.create({
        clientMsgId,
        convId: idToLong(convId),
        content,
      }),
    ).finish();
    this.sendRaw(WsCmd.MSG_SEND, body);
  }

  sendReadReport(convId: string, readSeq: string) {
    const body = ReadReport.encode(
      ReadReport.create({
        convId: idToLong(convId),
        readSeq: idToLong(readSeq),
      }),
    ).finish();
    this.sendRaw(WsCmd.READ_REPORT, body);
  }

  sendRecvAck(items: { convId: string; seq: string }[], reqId?: Long) {
    this.sendRaw(WsCmd.MSG_RECV_ACK, buildRecvAck(items), reqId);
  }

  private sendRaw(cmd: number, body?: Uint8Array, reqId?: Long) {
    if (this.ws?.readyState !== WebSocket.OPEN) {
      return;
    }
    this.ws.send(encodeFrame(cmd, body, reqId));
  }

  private scheduleReconnect() {
    useSocketStore.getState().setStatus('reconnecting');
    const delay = this.backoff.nextDelay();
    this.reconnectTimer = window.setTimeout(() => {
      const token = useAuthStore.getState().accessToken;
      if (token) {
        this.connect(token);
      }
    }, delay);
  }

  private clearReconnectTimer() {
    if (this.reconnectTimer) {
      window.clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      window.clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private closeSocket() {
    if (this.ws && this.ws.readyState !== WebSocket.CLOSED) {
      this.ws.close();
    }
    this.ws = null;
  }
}

export const imSocket = new ImSocket();

function decodeWsFrame(data: unknown) {
  if (data instanceof ArrayBuffer) {
    return decodeFrame(data);
  }
  throw new Error('Unsupported WebSocket payload');
}
