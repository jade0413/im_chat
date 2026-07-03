import Long from 'long';
import { APP_VERSION, createUuid, getOrCreateDeviceId, PLATFORM_WEB, REFRESH_TOKEN_KEY, TENANT_ID, WS_URL } from '../config';
import { AuthReq, decodeFrame, encodeFrame, MsgContent, MsgSend, ReadReport, SyncReq, WsCmd } from '../proto/codec';
import { refreshTokenDirect } from '../api/client';
import { useAuthStore } from '../store/authStore';
import { useConvStore } from '../store/convStore';
import { useMessageStore } from '../store/messageStore';
import { useSocketStore } from '../store/socketStore';
import { useUiStore } from '../store/uiStore';
import { idToLong } from '../utils/id';
import { resolveLocalSyncSeq } from '../utils/seq';
import { dispatchFrame, buildRecvAck } from './handlers';
import { ReconnectBackoff } from './reconnect';

/** 等待 MSG_SEND_ACK 的消息队列：clientMsgId → {convId, body, sentAt} */
interface PendingEntry {
  convId: string;
  body: Uint8Array;
  sentAt: number;
}

/** 重连后自动补发的超时门限（ms）；超时则标记 failed，不再补发 */
const PENDING_TTL_MS = 60_000;

/**
 * WebSocket 连接状态机
 *
 * 关键设计原则（参考 WeChat/Telegram/OpenIM）：
 *
 * 1. 普通断线（网络抖动）→ 指数退避重连，对用户透明
 * 2. AUTH_ACK 失败（token_ver 过期/被踢）
 *    → 先尝试刷新 token，成功则用新 token 重连
 *    → refresh 401/403 → 真正失效，弹下线提示
 *    → 同一次连接生命周期内只允许刷新一次，防止双设备 kick 互刷死循环
 * 3. KICK 帧收到
 *    → 当前连接被服务端明确下线，logout + 弹提示 + 停止重连
 */
export class ImSocket {
  private ws: WebSocket | null = null;
  private reconnectTimer: number | null = null;
  private heartbeatTimer: number | null = null;
  private openTimer: number | null = null;
  private authAckTimer: number | null = null;
  private manualClose = false;
  private generation = 0;
  private connectStartedAt = 0;
  private hasAuthAck = false;
  private lastFrameAt = 0;
  private lastFrameCmd = 0;
  /** 当前心跳周期（ms），存活探测用 */
  private heartbeatMs = 30_000;
  /** 网络恢复/可见性监听只绑定一次 */
  private netListenersBound = false;
  private readonly backoff = new ReconnectBackoff();

  /** 未收到服务端 ACK 的出站消息，重连后自动重发 */
  private readonly pendingMap = new Map<string, PendingEntry>();

  /**
   * 防止 AUTH 失败 → refresh → 重连 → AUTH 再次失败 无限循环。
   * 每次 connect() 重置；成功 AUTH_ACK 也重置。
   */
  private authRefreshAttempted = false;

  // ─── 公开连接控制 ──────────────────────────────────────────────

  connect(token: string) {
    this.bindNetworkListeners();
    const generation = ++this.generation;
    this.manualClose = false;
    this.connectStartedAt = Date.now();
    this.hasAuthAck = false;
    this.authRefreshAttempted = false; // 新连接周期重置
    this.lastFrameAt = 0;
    this.lastFrameCmd = 0;
    this.clearReconnectTimer();
    this.closeSocket();
    useSocketStore.getState().setStatus('connecting');
    useSocketStore.getState().setEvent(`准备连接 ${WS_URL}`);

    const ws = new WebSocket(WS_URL);
    this.ws = ws;
    ws.binaryType = 'arraybuffer';
    this.startOpenTimer(ws, generation);

    ws.onopen = () => {
      if (!this.isActiveSocket(ws, generation)) { ws.close(); return; }
      this.stopOpenTimer();
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
      console.info('[im-web/ws] socket opened, sending AUTH', { url: WS_URL, generation });
      useSocketStore.getState().setEvent('WebSocket 已打开，正在鉴权');
      this.startAuthAckTimer(ws, generation);
      this.sendRaw(WsCmd.AUTH, body);
    };

    ws.onmessage = (event) => {
      if (!this.isActiveSocket(ws, generation)) return;
      try {
        const frame = decodeWsFrame(event.data);
        this.lastFrameAt = Date.now();
        this.lastFrameCmd = frame.cmd ?? 0;
        if (frame.cmd === WsCmd.AUTH_ACK) this.hasAuthAck = true;
        console.debug('[im-web/ws] frame received', { cmd: frame.cmd, reqId: frame.reqId?.toString?.() ?? frame.reqId });
        useSocketStore.getState().setEvent(`收到下行帧 ${cmdLabel(frame.cmd ?? 0)}`);
        useSocketStore.getState().addLog(
          'info',
          `WS 下行 ${cmdLabel(frame.cmd ?? 0)}`,
          `reqId=${frame.reqId?.toString?.() ?? frame.reqId ?? 0}\nbodyBytes=${bodyLength(frame.body)}`,
        );
        dispatchFrame(this, frame);
      } catch (error) {
        console.error('[im-web] ws frame decode failed', error);
        useSocketStore.getState().setEvent('下行帧解析失败', error instanceof Error ? error.message : String(error));
      }
    };

    ws.onerror = () => {
      if (!this.isActiveSocket(ws, generation)) return;
      const detail = this.closeDebugDetail(ws.readyState);
      console.warn('[im-web/ws] socket error', { detail });
      this.stopOpenTimer();
      if (!this.hasAuthAck) {
        useSocketStore.getState().setStatus('error', 'WebSocket 连接异常');
        useSocketStore.getState().setEvent('WebSocket error', detail);
      } else {
        useSocketStore.getState().setEvent('WebSocket error', detail);
      }
    };

    ws.onclose = (event) => {
      if (!this.isActiveSocket(ws, generation)) return;
      console.warn('[im-web/ws] socket closed', {
        manualClose: this.manualClose,
        generation,
        code: event.code,
        reason: event.reason,
        wasClean: event.wasClean,
        authenticated: this.hasAuthAck,
        lastFrameCmd: this.lastFrameCmd,
      });
      this.stopOpenTimer();
      this.stopAuthAckTimer();
      this.stopHeartbeat();
      this.ws = null;
      const detail = this.closeDebugDetail(WebSocket.CLOSED, event);

      if (this.manualClose) {
        // 手动断开（logout / KICK 后主动关闭）→ 不重连
        useSocketStore.getState().setStatus('closed');
        useSocketStore.getState().setEvent(`WebSocket 已关闭 code=${event.code || 0}`, detail);
        return;
      }

      if (!useAuthStore.getState().accessToken) {
        // 已登出（accessToken 已被清除）→ 不重连
        useSocketStore.getState().setStatus('closed');
        return;
      }

      // 正常断线（网络抖动、服务器重启）→ 指数退避重连
      useSocketStore.getState().setEvent(
        this.hasAuthAck ? 'WebSocket 已断开，准备重连' : `WebSocket 已关闭 code=${event.code || 0}`,
        detail,
      );
      this.scheduleReconnect();
    };
  }

  disconnect(manual = true) {
    this.generation += 1;
    this.manualClose = manual;
    this.clearReconnectTimer();
    this.stopOpenTimer();
    this.stopAuthAckTimer();
    this.stopHeartbeat();
    this.closeSocket();
    useSocketStore.getState().setStatus('closed');
    useSocketStore.getState().setEvent('手动断开连接');
  }

  // ─── AUTH 相关 ──────────────────────────────────────────────────

  resetBackoff() { this.backoff.reset(); }

  /**
   * AUTH_ACK 成功时调用：重置 auth 刷新标记，并清除上次遗留的下线弹窗。
   */
  markAuthReady() {
    this.authRefreshAttempted = false;
    useUiStore.getState().setKickMessage(null);
  }

  /**
   * AUTH_ACK 失败时调用（服务端明确拒绝，非网络错误）。
   *
   * 流程（参考 Telegram/OpenIM）：
   *   1. 先尝试刷新 token（绕开 apiClient 拦截器）
   *   2. 成功 → 用新 token 重连；此连接周期内只刷新一次，再次失败直接下线提示
   *   3. 401/403 → refresh token 失效 → logout + 下线弹窗
   *   4. 网络错误 → 稍后重试（scheduleReconnect）
   */
  async handleAuthAckFailure(reason: string) {
    // 停止当前连接，防止继续发送帧
    this.manualClose = true;
    this.closeSocket();

    if (this.authRefreshAttempted) {
      // 上次刷新后重连仍然 AUTH 失败 → 彻底下线
      console.warn('[im-web/ws] AUTH failed after token refresh — logging out');
      useAuthStore.getState().logout();
      useUiStore.getState().setKickMessage(reason || '登录状态已失效，请重新登录');
      useSocketStore.getState().setStatus('closed');
      return;
    }

    const refreshToken = window.localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!refreshToken) {
      useAuthStore.getState().logout();
      useUiStore.getState().setKickMessage(reason || '登录已过期，请重新登录');
      useSocketStore.getState().setStatus('closed');
      return;
    }

    console.info('[im-web/ws] AUTH failed, attempting token refresh...');
    useSocketStore.getState().setStatus('reconnecting');
    useSocketStore.getState().setEvent('AUTH 失败，正在刷新登录凭证...');
    this.authRefreshAttempted = true;

    try {
      const tokens = await refreshTokenDirect(refreshToken);
      useAuthStore.getState().applyTokens(tokens);
      console.info('[im-web/ws] token refreshed, reconnecting...');
      // 用新 token 重连；authRefreshAttempted 保持 true，再次 AUTH 失败将直接下线
      this.manualClose = false;
      this.connect(tokens.accessToken);
    } catch (err) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 401 || status === 403) {
        // refresh token 也已失效（被踢 + refresh 过期，或账号被封禁）
        useAuthStore.getState().logout();
        useUiStore.getState().setKickMessage(reason || '账号已下线，请重新登录');
        useSocketStore.getState().setStatus('closed');
      } else {
        // 网络 / 服务器临时错误：稍后重试
        console.warn('[im-web/ws] token refresh network error, will retry', err);
        this.authRefreshAttempted = false; // 网络错误不算真实失败，允许下次重试
        this.manualClose = false;
        this.scheduleReconnect();
      }
    }
  }

  // ─── 心跳 ───────────────────────────────────────────────────────

  startHeartbeat() {
    this.stopAuthAckTimer();
    this.stopHeartbeat();
    this.heartbeatMs = Math.max(10, useSocketStore.getState().heartbeatIntervalSec) * 1000;
    // 刚鉴权成功，重置存活基准，避免下一拍误判
    this.lastFrameAt = Date.now();
    this.heartbeatTimer = window.setInterval(() => {
      // 存活探测：超过 2.5 个心跳周期没收到任何下行帧 → 判定半死链，主动断开并重连
      // （兜底 TCP 半开/移动网络切换时浏览器 onclose 迟迟不触发的场景）
      if (this.lastFrameAt > 0 && Date.now() - this.lastFrameAt > this.heartbeatMs * 2.5) {
        console.warn('[im-web/ws] no frame within liveness window, forcing reconnect');
        useSocketStore.getState().setEvent('心跳无响应，主动重连');
        this.forceReconnect();
        return;
      }
      this.sendRaw(WsCmd.PING);
    }, this.heartbeatMs);
  }

  /** 半死链时主动断开并调度重连（closeSocket 已摘除 onclose，需手动调度）。 */
  private forceReconnect() {
    this.stopHeartbeat();
    this.closeSocket();
    if (!this.manualClose && useAuthStore.getState().accessToken) {
      this.scheduleReconnect();
    }
  }

  /** 绑定一次：网络恢复 / 标签页重新可见时立即重连，不必等退避计时器。 */
  private bindNetworkListeners() {
    if (this.netListenersBound) return;
    this.netListenersBound = true;
    window.addEventListener('online', () => this.reconnectNow('network online'));
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') this.reconnectNow('tab visible');
    });
  }

  /** 网络/可见性恢复时的即时重连：已连接则探活，未连接则重置退避立刻重连。 */
  private reconnectNow(reason: string) {
    if (this.manualClose) return;
    const token = useAuthStore.getState().accessToken;
    if (!token) return;
    if (this.ws) {
      if (this.ws.readyState === WebSocket.OPEN) {
        // 已连接：主动探活一次，半死链交给心跳看门狗兜底
        this.sendRaw(WsCmd.PING);
        return;
      }
      if (this.ws.readyState === WebSocket.CONNECTING) {
        // 正在连接：交给 openTimer 处理，避免抖动
        return;
      }
    }
    console.info('[im-web/ws] immediate reconnect:', reason);
    useSocketStore.getState().setEvent('网络恢复，立即重连', reason);
    this.clearReconnectTimer();
    this.backoff.reset();
    this.connect(token);
  }

  // ─── 消息 ───────────────────────────────────────────────────────

  sendSyncReq() {
    const convState = useConvStore.getState();
    const body = SyncReq.encode(
      SyncReq.create({
        convListVersion: idToLong(convState.convListVersion),
        convVersions: Array.from(convState.conversations.values()).map((conv) => ({
          convId: idToLong(conv.convId),
          localMaxSeq: idToLong(this.localSyncSeq(conv.convId, conv.syncSeq)),
        })),
      }),
    ).finish();
    this.sendRaw(WsCmd.SYNC_REQ, body);
  }

  sendText(convId: string, text: string) {
    const auth = useAuthStore.getState();
    const clientMsgId = createUuid();
    useMessageStore.getState().addOptimistic({
      clientMsgId,
      convId,
      sender: {
        userId: String(auth.user?.id ?? '0'),
        nickname: auth.user?.nickname || auth.user?.account || '我',
        avatar: auth.user?.avatar,
      },
      content: { kind: 'text', text },
      sendTime: String(Date.now()),
      status: 'sending',
    });
    const content = MsgContent.create({ text: { text, atUserIds: [] } });
    const body = MsgSend.encode(MsgSend.create({ clientMsgId, convId: idToLong(convId), content })).finish();
    this._doSendMsg(clientMsgId, convId, body);
  }

  sendImage(
    convId: string,
    opts: { objectKey: string; thumbKey?: string; width?: number; height?: number; size?: number; mime?: string; previewUrl?: string },
  ) {
    const auth = useAuthStore.getState();
    const clientMsgId = createUuid();
    useMessageStore.getState().addOptimistic({
      clientMsgId,
      convId,
      sender: { userId: String(auth.user?.id ?? '0'), nickname: auth.user?.nickname || auth.user?.account || '我', avatar: auth.user?.avatar },
      content: { kind: 'image', ...opts },
      sendTime: String(Date.now()),
      status: 'sending',
    });
    const content = MsgContent.create({
      image: { objectKey: opts.objectKey, thumbKey: opts.thumbKey ?? '', width: opts.width ?? 0, height: opts.height ?? 0, size: opts.size ?? 0, mime: opts.mime ?? '' },
    });
    const body = MsgSend.encode(MsgSend.create({ clientMsgId, convId: idToLong(convId), content })).finish();
    this._doSendMsg(clientMsgId, convId, body);
  }

  sendFile(convId: string, opts: { objectKey: string; fileName: string; size?: number; mime?: string; thumbKey?: string; durationMs?: number }) {
    const auth = useAuthStore.getState();
    const clientMsgId = createUuid();
    useMessageStore.getState().addOptimistic({
      clientMsgId,
      convId,
      sender: { userId: String(auth.user?.id ?? '0'), nickname: auth.user?.nickname || auth.user?.account || '我', avatar: auth.user?.avatar },
      content: { kind: 'file', ...opts },
      sendTime: String(Date.now()),
      status: 'sending',
    });
    const content = MsgContent.create({
      file: {
        objectKey: opts.objectKey,
        fileName: opts.fileName,
        size: opts.size ?? 0,
        mime: opts.mime ?? '',
        thumbKey: opts.thumbKey ?? '',
        durationMs: opts.durationMs ?? 0,
      },
    });
    const body = MsgSend.encode(MsgSend.create({ clientMsgId, convId: idToLong(convId), content })).finish();
    this._doSendMsg(clientMsgId, convId, body);
  }

  sendVoice(convId: string, opts: { objectKey: string; durationMs: number; size?: number }) {
    const auth = useAuthStore.getState();
    const clientMsgId = createUuid();
    useMessageStore.getState().addOptimistic({
      clientMsgId,
      convId,
      sender: { userId: String(auth.user?.id ?? '0'), nickname: auth.user?.nickname || auth.user?.account || '我', avatar: auth.user?.avatar },
      content: { kind: 'voice', objectKey: opts.objectKey, durationMs: opts.durationMs, size: opts.size, codec: 'opus' },
      sendTime: String(Date.now()),
      status: 'sending',
    });
    const content = MsgContent.create({ voice: { objectKey: opts.objectKey, durationMs: opts.durationMs, size: opts.size ?? 0, codec: 'opus' } });
    const body = MsgSend.encode(MsgSend.create({ clientMsgId, convId: idToLong(convId), content })).finish();
    this._doSendMsg(clientMsgId, convId, body);
  }

  /** 内部：消息入队并尝试立即发送；WS 未就绪时保持 sending 状态等重连 */
  private _doSendMsg(clientMsgId: string, convId: string, body: Uint8Array) {
    this.pendingMap.set(clientMsgId, { convId, body, sentAt: Date.now() });
    if (!this.sendRaw(WsCmd.MSG_SEND, body)) {
      useSocketStore.getState().setEvent('消息入队等待重连', `clientMsgId=${clientMsgId}`);
    }
  }

  /** 收到 MSG_SEND_ACK 后移除 pending 记录，并返回原始会话 ID 供失败 ACK 回填 */
  dequeue(clientMsgId: string): string | undefined {
    const entry = this.pendingMap.get(clientMsgId);
    this.pendingMap.delete(clientMsgId);
    return entry?.convId;
  }

  /** 重连成功 AUTH_ACK 后补发所有 pending 消息 */
  drainPending() {
    if (this.pendingMap.size === 0) return;
    const now = Date.now();
    for (const [clientMsgId, entry] of this.pendingMap) {
      if (now - entry.sentAt > PENDING_TTL_MS) {
        useMessageStore.getState().updateByClientMsgId(entry.convId, clientMsgId, { status: 'failed' });
        this.pendingMap.delete(clientMsgId);
        useSocketStore.getState().setEvent('消息超时标记失败', `clientMsgId=${clientMsgId}`);
        continue;
      }
      this.sendRaw(WsCmd.MSG_SEND, entry.body);
      useSocketStore.getState().setEvent('补发 pending 消息', `clientMsgId=${clientMsgId}`);
    }
  }

  /** 用户手动重试 failed 消息 */
  retryMessage(convId: string, clientMsgId: string) {
    const list = useMessageStore.getState().messages.get(convId) ?? [];
    const msg = list.find((m) => m.clientMsgId === clientMsgId);
    if (!msg) return;
    const content = rebuildMsgContent(msg.content);
    if (!content) return;
    const body = MsgSend.encode(MsgSend.create({ clientMsgId, convId: idToLong(convId), content })).finish();
    useMessageStore.getState().updateByClientMsgId(convId, clientMsgId, { status: 'sending' });
    this._doSendMsg(clientMsgId, convId, body);
  }

  sendReadReport(convId: string, readSeq: string) {
    const body = ReadReport.encode(ReadReport.create({ convId: idToLong(convId), readSeq: idToLong(readSeq) })).finish();
    this.sendRaw(WsCmd.READ_REPORT, body);
  }

  sendRecvAck(items: { convId: string; seq: string }[], reqId?: Long) {
    this.sendRaw(WsCmd.MSG_RECV_ACK, buildRecvAck(items), reqId);
  }

  // ─── 私有工具 ────────────────────────────────────────────────────

  private sendRaw(cmd: number, body?: Uint8Array, reqId?: Long) {
    if (this.ws?.readyState !== WebSocket.OPEN) {
      useSocketStore.getState().setEvent(`发送跳过 cmd=${cmd}`, `readyState=${this.ws?.readyState ?? 'none'}`);
      return false;
    }
    this.ws.send(encodeFrame(cmd, body, reqId));
    useSocketStore.getState().setEvent(`已发送上行帧 ${cmdLabel(cmd)}`);
    useSocketStore.getState().addLog(
      'info',
      `WS 上行 ${cmdLabel(cmd)}`,
      `reqId=${reqId?.toString?.() ?? 'auto'}\nbodyBytes=${body?.byteLength ?? 0}`,
    );
    return true;
  }

  private localSyncSeq(convId: string, storedSyncSeq?: string): string {
    return resolveLocalSyncSeq(storedSyncSeq, useMessageStore.getState().messages.get(convId));
  }

  private scheduleReconnect() {
    useSocketStore.getState().setStatus('reconnecting');
    const delay = this.backoff.nextDelay();
    useSocketStore.getState().setEvent(`准备重连 ${delay}ms 后`);
    this.reconnectTimer = window.setTimeout(() => {
      const token = useAuthStore.getState().accessToken;
      if (token) {
        this.connect(token);
      }
    }, delay);
  }

  private clearReconnectTimer() {
    if (this.reconnectTimer) { window.clearTimeout(this.reconnectTimer); this.reconnectTimer = null; }
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) { window.clearInterval(this.heartbeatTimer); this.heartbeatTimer = null; }
  }

  private startAuthAckTimer(ws: WebSocket, generation: number) {
    this.stopAuthAckTimer();
    this.authAckTimer = window.setTimeout(() => {
      if (!this.isActiveSocket(ws, generation)) return;
      console.warn('[im-web/ws] AUTH_ACK timeout, reconnecting');
      useSocketStore.getState().setStatus('error', '等待网关鉴权响应超时');
      useSocketStore.getState().setEvent('等待 AUTH_ACK 超时', '等待网关鉴权响应超时');
      ws.close();
    }, 8000);
  }

  private startOpenTimer(ws: WebSocket, generation: number) {
    this.stopOpenTimer();
    this.openTimer = window.setTimeout(() => {
      if (!this.isActiveSocket(ws, generation) || ws.readyState === WebSocket.OPEN) return;
      console.warn('[im-web/ws] open timeout', { readyState: ws.readyState, url: WS_URL });
      useSocketStore.getState().setStatus('error', 'WebSocket 打开超时');
      useSocketStore.getState().setEvent('WebSocket 打开超时', `readyState=${ws.readyState} url=${WS_URL}`);
      ws.close();
    }, 8000);
  }

  private stopOpenTimer() {
    if (this.openTimer) { window.clearTimeout(this.openTimer); this.openTimer = null; }
  }

  private stopAuthAckTimer() {
    if (this.authAckTimer) { window.clearTimeout(this.authAckTimer); this.authAckTimer = null; }
  }

  private closeSocket() {
    if (this.ws && this.ws.readyState !== WebSocket.CLOSED) {
      this.ws.onopen = null;
      this.ws.onmessage = null;
      this.ws.onerror = null;
      this.ws.onclose = null;
      this.ws.close();
    }
    this.ws = null;
  }

  private isActiveSocket(ws: WebSocket, generation: number) {
    return this.ws === ws && this.generation === generation;
  }

  private closeDebugDetail(readyState: number, event?: CloseEvent) {
    const uptimeMs = this.connectStartedAt ? Date.now() - this.connectStartedAt : 0;
    const sinceLastFrameMs = this.lastFrameAt ? Date.now() - this.lastFrameAt : -1;
    return [
      `code=${event?.code ?? 0}`,
      `clean=${event?.wasClean ?? false}`,
      `readyState=${readyState}`,
      `authenticated=${this.hasAuthAck}`,
      `uptimeMs=${uptimeMs}`,
      `lastFrame=${cmdLabel(this.lastFrameCmd)}`,
      `sinceLastFrameMs=${sinceLastFrameMs}`,
      event?.reason ? `reason=${event.reason}` : undefined,
    ].filter(Boolean).join(' ');
  }
}

export const imSocket = new ImSocket();

/** 从 store 里的 MessageContent 重建 proto MsgContent（用于重试） */
function rebuildMsgContent(content: import('../store/types').MessageContent): ReturnType<typeof MsgContent.create> | null {
  switch (content.kind) {
    case 'text':
      return MsgContent.create({ text: { text: content.text, atUserIds: [] } });
    case 'image':
      return MsgContent.create({
        image: { objectKey: content.objectKey, thumbKey: content.thumbKey ?? '', width: content.width ?? 0, height: content.height ?? 0, size: content.size ?? 0, mime: content.mime ?? '' },
      });
    case 'file':
    case 'video':
      return MsgContent.create({
        file: {
          objectKey: content.objectKey,
          fileName: content.fileName,
          size: content.size ?? 0,
          mime: content.mime ?? '',
          thumbKey: content.kind === 'video' ? content.thumbKey ?? '' : '',
          durationMs: content.kind === 'video' ? content.durationMs ?? 0 : 0,
        },
      });
    case 'voice':
      return MsgContent.create({
        voice: { objectKey: content.objectKey, durationMs: content.durationMs, size: content.size ?? 0, codec: content.codec ?? 'opus' },
      });
    default:
      return null;
  }
}

function decodeWsFrame(data: unknown) {
  if (data instanceof ArrayBuffer) return decodeFrame(data);
  throw new Error('Unsupported WebSocket payload');
}

function cmdLabel(cmd: number) { return `${WsCmd[cmd] ?? 'UNKNOWN'}(${cmd})`; }
function bodyLength(body: unknown) { return body instanceof Uint8Array ? body.byteLength : 0; }
