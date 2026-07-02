use crate::{
    frame_codec,
    proto::im::{
        rpc::v1::{ConnCtx, VerifyTokenReq},
        ws::v1::{AuthReq, AuthResp, Cmd, Frame, KickNotify},
    },
    state::AppState,
};
use anyhow::{Context, Result};
use axum::{
    extract::{
        connect_info::ConnectInfo,
        ws::{Message, WebSocket, WebSocketUpgrade},
        State,
    },
    http::{header::ORIGIN, HeaderMap, StatusCode},
    response::IntoResponse,
};
use bytes::Bytes;
use dashmap::DashMap;
use futures_util::{Sink, SinkExt, Stream, StreamExt};
use prost::Message as _;
use std::{
    hash::{Hash, Hasher},
    sync::{
        atomic::{AtomicU64, Ordering},
        Arc,
    },
    time::{Duration, Instant, SystemTime, UNIX_EPOCH},
};
use tokio::{
    sync::{mpsc, watch},
    time::{self, MissedTickBehavior},
};
use tracing::{debug, info, warn};
use uuid::Uuid;

// Connection-layer codes mirrored from common/error.proto and im-common ErrorCode.
const OK: i32 = 0;
const TOKEN_INVALID: i32 = 1001;
const REPLAY_REJECTED: i32 = 1005;
const INTERNAL_ERROR: i32 = 9999;
const PROTO_TOO_OLD_REASON: i32 = 4;

/// R2：pending ack 过期扫描粒度。10s 超时语义为约值，±1s 精度足够。
const ACK_SWEEP_INTERVAL: Duration = Duration::from_secs(1);

#[derive(Clone, Debug, Eq)]
pub struct ConnKey {
    tenant_id: i64,
    user_id: i64,
    platform: i32,
    conn_id: String,
}

impl ConnKey {
    pub fn new(tenant_id: i64, user_id: i64, platform: i32, conn_id: impl Into<String>) -> Self {
        Self {
            tenant_id,
            user_id,
            platform,
            conn_id: conn_id.into(),
        }
    }

    fn from_ctx(ctx: &ConnCtx) -> Self {
        Self::new(
            ctx.tenant_id,
            ctx.user_id,
            ctx.platform,
            ctx.conn_id.clone(),
        )
    }
}

impl PartialEq for ConnKey {
    fn eq(&self, other: &Self) -> bool {
        self.tenant_id == other.tenant_id
            && self.user_id == other.user_id
            && self.platform == other.platform
            && self.conn_id == other.conn_id
    }
}

impl Hash for ConnKey {
    fn hash<H: Hasher>(&self, state: &mut H) {
        self.tenant_id.hash(state);
        self.user_id.hash(state);
        self.platform.hash(state);
        self.conn_id.hash(state);
    }
}

#[derive(Clone)]
pub struct ConnectionRegistry {
    inner: Arc<DashMap<ConnKey, ConnectionHandle>>,
}

impl ConnectionRegistry {
    pub fn new() -> Self {
        Self {
            inner: Arc::new(DashMap::new()),
        }
    }

    pub fn insert(&self, handle: ConnectionHandle) {
        self.inner.insert(handle.key.clone(), handle);
    }

    pub fn get(
        &self,
        tenant_id: i64,
        user_id: i64,
        platform: i32,
        conn_id: &str,
    ) -> Option<ConnectionHandle> {
        self.inner
            .get(&ConnKey::new(tenant_id, user_id, platform, conn_id))
            .map(|entry| entry.value().clone())
    }

    pub fn remove(&self, key: &ConnKey) -> Option<ConnectionHandle> {
        self.inner.remove(key).map(|(_, value)| value)
    }

    pub fn len(&self) -> usize {
        self.inner.len()
    }

    /// R2：pending ack 归属各连接后，全局总数由此汇总（仅 /metrics 低频调用）。
    pub fn pending_acks_total(&self) -> usize {
        self.inner
            .iter()
            .map(|entry| entry.value().pending_len())
            .sum()
    }

    pub fn close_all(&self) -> usize {
        let handles = self
            .inner
            .iter()
            .map(|entry| entry.value().clone())
            .collect::<Vec<_>>();
        let count = handles.len();
        for handle in handles {
            handle.close();
        }
        count
    }
}

#[derive(Clone)]
pub struct ConnectionHandle {
    key: ConnKey,
    ctx: ConnCtx,
    sender: mpsc::Sender<Outbound>,
    close_tx: watch::Sender<bool>,
    next_push_req_id: Arc<AtomicU64>,
    heartbeat_count: Arc<AtomicU64>,
    outbound_full_count: Arc<AtomicU64>,
    outbound_full_disconnect_threshold: u64,
    // R2：pending ack 是连接局部状态（req_id -> 过期时刻）。
    // 随 handle 一起销毁，断连清理 O(1)，不再需要全局 PendingAcks 表、
    // 不再 per-push spawn 定时任务、不再有断连时的全表 retain。
    pending_acks: Arc<DashMap<u64, Instant>>,
}

impl ConnectionHandle {
    fn new(
        ctx: ConnCtx,
        sender: mpsc::Sender<Outbound>,
        close_tx: watch::Sender<bool>,
        outbound_full_disconnect_threshold: u64,
    ) -> Self {
        Self {
            key: ConnKey::from_ctx(&ctx),
            ctx,
            sender,
            close_tx,
            next_push_req_id: Arc::new(AtomicU64::new(1)),
            heartbeat_count: Arc::new(AtomicU64::new(0)),
            outbound_full_count: Arc::new(AtomicU64::new(0)),
            outbound_full_disconnect_threshold: outbound_full_disconnect_threshold.max(1),
            pending_acks: Arc::new(DashMap::new()),
        }
    }

    pub fn ctx(&self) -> ConnCtx {
        self.ctx.clone()
    }

    pub fn key(&self) -> ConnKey {
        self.key.clone()
    }

    /// need_ack 推送：req_id 逐连接分配，帧需逐个编码（req_id 不同无法共享）。
    /// body 为 `Bytes`，clone 仅引用计数（R1）。
    pub fn send_push(
        &self,
        cmd: u32,
        body: Bytes,
        need_ack: bool,
        ack_timeout: Duration,
    ) -> PushSendResult {
        if cmd > i32::MAX as u32 {
            warn!(cmd, "skip push with invalid cmd");
            return PushSendResult::Dropped;
        }
        let req_id = if need_ack {
            self.next_push_req_id.fetch_add(1, Ordering::Relaxed)
        } else {
            0
        };
        if need_ack {
            // 先登记再发送，杜绝"ack 先于登记到达"的竞态窗口。
            self.pending_acks.insert(req_id, Instant::now() + ack_timeout);
        }
        let frame = frame_codec::new_frame(cmd as i32, req_id, body);
        match self.send_frame(frame) {
            FrameSendResult::Sent => PushSendResult::Sent,
            FrameSendResult::Dropped => {
                if need_ack {
                    self.pending_acks.remove(&req_id);
                }
                PushSendResult::Dropped
            }
            FrameSendResult::Disconnected => {
                if need_ack {
                    self.pending_acks.remove(&req_id);
                }
                PushSendResult::Disconnected
            }
        }
    }

    /// 广播推送（need_ack=false，req_id 恒 0）：所有目标共享同一段预编码帧字节，
    /// clone 仅引用计数，零编码零拷贝（R1）。
    pub fn send_encoded(&self, frame_bytes: Bytes) -> PushSendResult {
        match self.try_send(Outbound::Encoded(frame_bytes)) {
            FrameSendResult::Sent => PushSendResult::Sent,
            FrameSendResult::Dropped => PushSendResult::Dropped,
            FrameSendResult::Disconnected => PushSendResult::Disconnected,
        }
    }

    /// 客户端 MSG_RECV_ACK：清除本连接 pending 记录。
    pub fn ack(&self, req_id: u64) -> bool {
        if req_id == 0 {
            return false;
        }
        self.pending_acks.remove(&req_id).is_some()
    }

    pub fn pending_len(&self) -> usize {
        self.pending_acks.len()
    }

    fn has_expired_ack(&self, now: Instant) -> bool {
        !self.pending_acks.is_empty()
            && self
                .pending_acks
                .iter()
                .any(|entry| *entry.value() <= now)
    }

    fn send_frame(&self, frame: Frame) -> FrameSendResult {
        self.try_send(Outbound::Frame(frame))
    }

    fn try_send(&self, outbound: Outbound) -> FrameSendResult {
        match self.sender.try_send(outbound) {
            Ok(()) => {
                self.outbound_full_count.store(0, Ordering::Relaxed);
                FrameSendResult::Sent
            }
            Err(mpsc::error::TrySendError::Full(_)) => {
                let full_count = self.outbound_full_count.fetch_add(1, Ordering::Relaxed) + 1;
                warn!(
                    tenant_id = self.ctx.tenant_id,
                    user_id = self.ctx.user_id,
                    platform = self.ctx.platform,
                    conn_id = self.ctx.conn_id,
                    full_count,
                    threshold = self.outbound_full_disconnect_threshold,
                    "outbound queue full"
                );
                if full_count >= self.outbound_full_disconnect_threshold {
                    warn!(
                        tenant_id = self.ctx.tenant_id,
                        user_id = self.ctx.user_id,
                        platform = self.ctx.platform,
                        conn_id = self.ctx.conn_id,
                        "outbound queue full threshold reached, close connection"
                    );
                    self.close();
                    FrameSendResult::Disconnected
                } else {
                    FrameSendResult::Dropped
                }
            }
            Err(mpsc::error::TrySendError::Closed(_)) => FrameSendResult::Disconnected,
        }
    }

    pub fn close(&self) {
        let _ = self.close_tx.send(true);
    }

    fn should_refresh_route(&self, interval: u64) -> bool {
        let interval = interval.max(1);
        self.heartbeat_count.fetch_add(1, Ordering::Relaxed) % interval == interval - 1
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum PushSendResult {
    Sent,
    Dropped,
    Disconnected,
}

enum Outbound {
    /// 逐帧编码路径（连接层应答、need_ack 推送等 req_id 各异的帧）。
    Frame(Frame),
    /// 预编码共享路径（广播推送，R1：一次编码，多目标引用计数共享）。
    Encoded(Bytes),
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum FrameSendResult {
    Sent,
    Dropped,
    Disconnected,
}

pub async fn ws_handler(
    State(state): State<AppState>,
    ConnectInfo(peer_addr): ConnectInfo<std::net::SocketAddr>,
    headers: HeaderMap,
    ws: WebSocketUpgrade,
) -> impl IntoResponse {
    if !state.lifecycle.is_ready() {
        state.metrics.handshake_rejected_draining();
        warn!(%peer_addr, "websocket handshake rejected because gateway is draining");
        return StatusCode::SERVICE_UNAVAILABLE.into_response();
    }
    if !state.handshake_limiter.allow() {
        state.metrics.handshake_rejected_rate_limit();
        warn!(%peer_addr, "websocket handshake rejected by rate limit");
        return StatusCode::TOO_MANY_REQUESTS.into_response();
    }
    if !state.ip_handshake_limiter.allow(peer_addr.ip()) {
        state.metrics.handshake_rejected_per_ip_rate_limit();
        warn!(%peer_addr, "websocket handshake rejected by per-ip rate limit");
        return StatusCode::TOO_MANY_REQUESTS.into_response();
    }
    if !origin_allowed(
        headers.get(ORIGIN).and_then(|value| value.to_str().ok()),
        &state.config.allowed_origins,
    ) {
        state.metrics.handshake_rejected_origin();
        warn!(%peer_addr, "websocket handshake rejected by origin policy");
        return StatusCode::FORBIDDEN.into_response();
    }
    ws.on_upgrade(move |socket| handle_socket(socket, state))
}

async fn handle_socket(socket: WebSocket, state: AppState) {
    let (ws_sender, ws_receiver) = socket.split();
    if let Err(err) = run_connection(ws_sender, ws_receiver, state).await {
        warn!(error = %format!("{err:#}"), "websocket connection closed with error");
    }
}

/// 连接状态机主体（R5）：收发两端泛型化（任意 Sink/Stream），
/// 上游依赖走 `Upstream` trait —— 生产为 axum WebSocket + gRPC，
/// 测试用内存 channel + mock 即可覆盖认证/重放/踢线/ack 全部分支。
pub(crate) async fn run_connection<Tx, Rx, E>(
    mut ws_sender: Tx,
    mut ws_receiver: Rx,
    state: AppState,
) -> Result<()>
where
    Tx: Sink<Message> + Unpin + Send + 'static,
    Tx::Error: std::error::Error + Send + Sync + 'static,
    Rx: Stream<Item = std::result::Result<Message, E>> + Unpin,
    E: std::error::Error + Send + Sync + 'static,
{
    let first_frame = read_auth_frame(
        &mut ws_receiver,
        state.config.auth_timeout,
        state.config.max_frame_bytes,
    )
    .await?;
    if first_frame.version < state.config.min_protocol_version {
        send_kick(&mut ws_sender, PROTO_TOO_OLD_REASON, "protocol too old").await?;
        return Ok(());
    }
    if first_frame.cmd != Cmd::Auth as i32 {
        send_auth_resp(&mut ws_sender, TOKEN_INVALID, "AUTH is required", 0, 0).await?;
        return Ok(());
    }

    let auth = AuthReq::decode(first_frame.body.clone()).context("invalid auth body")?;
    let now = unix_ts();
    if !timestamp_in_window(now, auth.timestamp, state.config.auth_replay_window) {
        send_auth_resp(
            &mut ws_sender,
            REPLAY_REJECTED,
            "request replay rejected",
            0,
            0,
        )
        .await?;
        return Ok(());
    }

    let verify_started = Instant::now();
    let verify = match state
        .rpc
        .verify_token(VerifyTokenReq {
            token: auth.token,
            tenant_id: auth.tenant_id,
            device_id: auth.device_id.clone(),
            platform: auth.platform,
            gw_instance: state.config.instance_id.clone(),
        })
        .await
    {
        Ok(response) => response,
        Err(err) => {
            warn!(
                tenant_id = auth.tenant_id,
                platform = auth.platform,
                device_id = %auth.device_id,
                elapsed_ms = verify_started.elapsed().as_millis(),
                error = %format!("{err:#}"),
                "verify token rpc failed"
            );
            send_auth_resp(&mut ws_sender, TOKEN_INVALID, "token invalid", 0, 0).await?;
            return Ok(());
        }
    };

    if verify.code != OK {
        send_auth_resp(
            &mut ws_sender,
            verify.code,
            &verify.message,
            verify.user_id,
            verify.heartbeat_interval_sec,
        )
        .await?;
        return Ok(());
    }

    let ctx = ConnCtx {
        tenant_id: auth.tenant_id,
        user_id: verify.user_id,
        platform: auth.platform,
        device_id: auth.device_id,
        conn_id: Uuid::new_v4().to_string(),
        gw_instance: state.config.instance_id.clone(),
        trace_id: Uuid::new_v4().to_string(),
    };

    let (outbound_tx, outbound_rx) = mpsc::channel(state.config.outbound_queue_size);
    let (close_tx, close_rx) = watch::channel(false);
    let handle = ConnectionHandle::new(
        ctx.clone(),
        outbound_tx,
        close_tx,
        state.config.outbound_queue_full_disconnect_threshold,
    );
    let key = handle.key();
    state.registry.insert(handle.clone());

    let on_connected_started = Instant::now();
    if let Err(err) = state.rpc.on_connected(ctx.clone()).await {
        warn!(
            tenant_id = ctx.tenant_id,
            user_id = ctx.user_id,
            platform = ctx.platform,
            device_id = %ctx.device_id,
            conn_id = %ctx.conn_id,
            elapsed_ms = on_connected_started.elapsed().as_millis(),
            error = %format!("{err:#}"),
            "on_connected rpc failed"
        );
        state.registry.remove(&key);
        send_auth_resp(
            &mut ws_sender,
            INTERNAL_ERROR,
            "failed to register connection",
            0,
            0,
        )
        .await?;
        return Ok(());
    }

    send_auth_resp(
        &mut ws_sender,
        OK,
        "ok",
        verify.user_id,
        verify.heartbeat_interval_sec,
    )
    .await?;

    state.metrics.connection_opened(ctx.tenant_id);
    info!(
        tenant_id = ctx.tenant_id,
        user_id = ctx.user_id,
        platform = ctx.platform,
        conn_id = ctx.conn_id,
        trace_id = ctx.trace_id,
        "websocket authenticated"
    );

    let writer = tokio::spawn(writer_loop(
        ws_sender,
        outbound_rx,
        close_rx,
        handle.clone(),
        state.clone(),
        ctx.clone(),
    ));

    let heartbeat_interval = normalize_heartbeat_interval(verify.heartbeat_interval_sec);
    let read_result = read_loop(
        &mut ws_receiver,
        ctx.clone(),
        handle.clone(),
        state.clone(),
        heartbeat_interval,
    )
    .await;
    // R2：pending ack 随 handle 一起消亡，无需全局取消。
    if state.registry.remove(&key).is_some() {
        state.metrics.connection_closed(ctx.tenant_id);
        if let Err(err) = state.rpc.on_disconnected(ctx).await {
            warn!(?err, "on_disconnected rpc failed");
        }
        handle.close();
    }
    let _ = writer.await;
    read_result
}

/// 下行写循环 + pending ack 过期扫描（R2）。
/// 扫描只遍历本连接在途 ack（量级 = 单连接未确认推送数），
/// 超时判定半死链：本地摘除路由并回报 Java，随后关闭 socket。
async fn writer_loop<Tx>(
    mut ws_sender: Tx,
    mut outbound_rx: mpsc::Receiver<Outbound>,
    mut close_rx: watch::Receiver<bool>,
    handle: ConnectionHandle,
    state: AppState,
    ctx: ConnCtx,
) where
    Tx: Sink<Message> + Unpin + Send + 'static,
    Tx::Error: std::error::Error + Send + Sync + 'static,
{
    let mut sweep = time::interval(ACK_SWEEP_INTERVAL);
    sweep.set_missed_tick_behavior(MissedTickBehavior::Delay);
    loop {
        tokio::select! {
            // biased：已入队的下行帧优先于 close 信号刷出，保证 KICK/最后一批推送
            // 在关闭前送达（close 后无新生产者，无饿死风险）。
            biased;
            outbound = outbound_rx.recv() => {
                let Some(outbound) = outbound else {
                    break;
                };
                let message = match outbound {
                    Outbound::Frame(frame) => Message::Binary(frame_codec::encode(&frame)),
                    Outbound::Encoded(frame_bytes) => Message::Binary(frame_bytes),
                };
                if ws_sender.send(message).await.is_err() {
                    break;
                }
            }
            changed = close_rx.changed() => {
                if changed.is_ok() && *close_rx.borrow() {
                    let _ = ws_sender.send(Message::Close(None)).await;
                }
                break;
            }
            _ = sweep.tick() => {
                if handle.has_expired_ack(Instant::now()) {
                    warn!(
                        tenant_id = ctx.tenant_id,
                        user_id = ctx.user_id,
                        platform = ctx.platform,
                        conn_id = ctx.conn_id,
                        "push ack timeout, close connection"
                    );
                    if state.registry.remove(&handle.key()).is_some() {
                        state.metrics.connection_closed(ctx.tenant_id);
                        state.metrics.ack_timeout_disconnect();
                        let rpc = state.rpc.clone();
                        let disconnected_ctx = ctx.clone();
                        tokio::spawn(async move {
                            if let Err(err) = rpc.on_disconnected(disconnected_ctx).await {
                                warn!(?err, "failed to report disconnected after push ack timeout");
                            }
                        });
                    }
                    let _ = ws_sender.send(Message::Close(None)).await;
                    break;
                }
            }
        }
    }
}

async fn read_loop<Rx, E>(
    receiver: &mut Rx,
    ctx: ConnCtx,
    handle: ConnectionHandle,
    state: AppState,
    heartbeat_interval: Duration,
) -> Result<()>
where
    Rx: Stream<Item = std::result::Result<Message, E>> + Unpin,
    E: std::error::Error + Send + Sync + 'static,
{
    // C-5：持有本连接 handle，避免对每个上行帧都做并发哈希查找 + 克隆整个 ConnectionHandle。
    // 连接被踢/关闭由 writer 收到 close 信号关 socket → receiver 结束来感知，无需靠"查不到"判活。
    let idle_timeout = heartbeat_interval * 3;
    while let Ok(message) = time::timeout(idle_timeout, receiver.next()).await {
        let Some(message) = message else {
            break;
        };
        let message = message?;
        let payload = match message {
            Message::Binary(payload) => payload,
            Message::Close(_) => {
                handle.close();
                break;
            }
            _ => continue,
        };
        let frame = frame_codec::decode_with_limit(payload, state.config.max_frame_bytes)?;
        if frame.version < state.config.min_protocol_version {
            let body = Bytes::from(
                KickNotify {
                    reason: PROTO_TOO_OLD_REASON,
                    message: "protocol too old".to_string(),
                }
                .encode_to_vec(),
            );
            handle.send_frame(frame_codec::new_frame(Cmd::Kick as i32, 0, body));
            handle.close();
            break;
        }
        match frame.cmd {
            cmd if cmd == Cmd::Ping as i32 => {
                handle.send_frame(frame_codec::new_frame(
                    Cmd::Pong as i32,
                    frame.req_id,
                    Bytes::new(),
                ));
                if handle.should_refresh_route(state.config.route_renew_heartbeat_interval) {
                    let rpc = state.rpc.clone();
                    let heartbeat_ctx = ctx.clone();
                    tokio::spawn(async move {
                        if let Err(err) = rpc.refresh_route(heartbeat_ctx).await {
                            warn!(?err, "failed to refresh route on heartbeat");
                        }
                    });
                }
            }
            cmd if cmd == Cmd::MsgRecvAck as i32 => {
                // R2：ack 直达本连接 handle，无全局表查找。
                let acked = handle.ack(frame.req_id);
                debug!(
                    tenant_id = ctx.tenant_id,
                    user_id = ctx.user_id,
                    conn_id = ctx.conn_id,
                    req_id = frame.req_id,
                    acked,
                    "received push ack"
                );
                // 送达回执转达 Java 为尽力而为：spawn 出去，避免每条 ACK 都让读循环
                // 阻塞一个 gRPC 往返（高吞吐下会拖慢同连接后续上行帧的处理）。
                let ack_rpc = state.rpc.clone();
                let ack_ctx = ctx.clone();
                let ack_body = frame.body;
                tokio::spawn(async move {
                    if let Err(err) = ack_rpc.on_push_acked(ack_ctx, ack_body).await {
                        warn!(?err, "on_push_acked rpc failed");
                    }
                });
            }
            cmd if cmd == Cmd::Auth as i32 => {
                warn!("duplicate AUTH frame, close connection");
                break;
            }
            cmd if cmd >= 0 => {
                let cmd = cmd as u32;
                state.metrics.uplink_frame(cmd);
                let dispatch_started = Instant::now();
                match state
                    .rpc
                    .dispatch(ctx.clone(), cmd, frame.body, frame.req_id)
                    .await
                {
                    Ok(response) => {
                        state.metrics.dispatch_completed(
                            true,
                            dispatch_started.elapsed().as_millis() as u64,
                        );
                        handle.send_frame(frame_codec::new_frame(
                            response.cmd as i32,
                            frame.req_id,
                            response.body,
                        ));
                    }
                    Err(err) => {
                        state.metrics.dispatch_completed(
                            false,
                            dispatch_started.elapsed().as_millis() as u64,
                        );
                        warn!(
                            ?err,
                            tenant_id = ctx.tenant_id,
                            user_id = ctx.user_id,
                            conn_id = ctx.conn_id,
                            trace_id = ctx.trace_id,
                            cmd,
                            req_id = frame.req_id,
                            "dispatch failed"
                        );
                        handle.send_frame(frame_codec::new_frame(
                            Cmd::Error as i32,
                            frame.req_id,
                            frame_codec::gateway_error_body(
                                INTERNAL_ERROR,
                                "upstream unavailable",
                                frame.req_id,
                            ),
                        ));
                    }
                }
            }
            _ => warn!(cmd = frame.cmd, "skip invalid negative cmd"),
        }
    }
    warn!(
        tenant_id = ctx.tenant_id,
        user_id = ctx.user_id,
        platform = ctx.platform,
        conn_id = ctx.conn_id,
        timeout_ms = idle_timeout.as_millis(),
        "connection idle timeout or closed"
    );
    Ok(())
}

async fn read_auth_frame<Rx, E>(
    receiver: &mut Rx,
    timeout: Duration,
    max_frame_bytes: usize,
) -> Result<Frame>
where
    Rx: Stream<Item = std::result::Result<Message, E>> + Unpin,
    E: std::error::Error + Send + Sync + 'static,
{
    let next = time::timeout(timeout, receiver.next())
        .await
        .context("AUTH timeout")?
        .context("websocket closed before AUTH")??;
    match next {
        Message::Binary(payload) => frame_codec::decode_with_limit(payload, max_frame_bytes),
        _ => anyhow::bail!("AUTH must be sent as binary protobuf frame"),
    }
}

async fn send_auth_resp<S>(
    sender: &mut S,
    code: i32,
    message: &str,
    user_id: i64,
    heartbeat_interval_sec: u32,
) -> Result<()>
where
    S: Sink<Message> + Unpin,
    S::Error: std::error::Error + Send + Sync + 'static,
{
    let body = Bytes::from(
        AuthResp {
            code,
            message: message.to_string(),
            user_id,
            server_ts: unix_ts(),
            heartbeat_interval_sec,
        }
        .encode_to_vec(),
    );
    sender
        .send(Message::Binary(frame_codec::encode(
            &frame_codec::new_frame(Cmd::AuthAck as i32, 0, body),
        )))
        .await
        .context("failed to send auth response")?;
    Ok(())
}

async fn send_kick<S>(sender: &mut S, reason: i32, message: &str) -> Result<()>
where
    S: Sink<Message> + Unpin,
    S::Error: std::error::Error + Send + Sync + 'static,
{
    let body = Bytes::from(
        KickNotify {
            reason,
            message: message.to_string(),
        }
        .encode_to_vec(),
    );
    sender
        .send(Message::Binary(frame_codec::encode(
            &frame_codec::new_frame(Cmd::Kick as i32, 0, body),
        )))
        .await
        .context("failed to send kick")?;
    sender
        .send(Message::Close(None))
        .await
        .context("failed to close websocket")?;
    Ok(())
}

fn timestamp_in_window(now: i64, timestamp: i64, window: Duration) -> bool {
    timestamp > 0 && now.abs_diff(timestamp) <= window.as_secs()
}

fn normalize_heartbeat_interval(heartbeat_interval_sec: u32) -> Duration {
    Duration::from_secs(u64::from(heartbeat_interval_sec.max(30)))
}

fn unix_ts() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs() as i64
}

fn origin_allowed(origin: Option<&str>, allowed_origins: &[String]) -> bool {
    let Some(origin) = origin else {
        return true;
    };
    allowed_origins
        .iter()
        .any(|allowed| allowed == "*" || allowed.eq_ignore_ascii_case(origin))
}

#[cfg(test)]
mod tests {
    use super::{
        frame_codec, normalize_heartbeat_interval, origin_allowed, run_connection,
        timestamp_in_window, ConnCtx, ConnectionHandle, ConnectionRegistry, FrameSendResult,
        Message, PushSendResult,
    };
    use crate::{
        config::Config,
        handshake_limiter::{HandshakeLimiter, IpHandshakeLimiter},
        metrics::Metrics,
        proto::im::{
            rpc::v1::{Empty, UplinkResp, VerifyTokenReq, VerifyTokenResp},
            ws::v1::{AuthReq, AuthResp, Cmd},
        },
        rpc::Upstream,
        state::{AppState, Lifecycle},
    };
    use anyhow::Result;
    use bytes::Bytes;
    use prost::Message as _;
    use std::{
        sync::{Arc, Mutex as StdMutex},
        time::{Duration, Instant},
    };
    use tokio::sync::{mpsc, watch};

    #[test]
    fn validates_auth_timestamp_window() {
        assert!(timestamp_in_window(1_000, 999, Duration::from_secs(5)));
        assert!(timestamp_in_window(1_000, 1_005, Duration::from_secs(5)));
        assert!(!timestamp_in_window(1_000, 994, Duration::from_secs(5)));
        assert!(!timestamp_in_window(1_000, 0, Duration::from_secs(5)));
    }

    #[test]
    fn heartbeat_interval_has_safe_default_floor() {
        assert_eq!(normalize_heartbeat_interval(0), Duration::from_secs(30));
        assert_eq!(normalize_heartbeat_interval(10), Duration::from_secs(30));
        assert_eq!(normalize_heartbeat_interval(45), Duration::from_secs(45));
    }

    #[test]
    fn validates_origin_against_allowlist() {
        let allowed = vec!["https://app.example.com".to_string()];

        assert!(origin_allowed(None, &allowed));
        assert!(origin_allowed(Some("https://app.example.com"), &allowed));
        assert!(origin_allowed(Some("HTTPS://APP.EXAMPLE.COM"), &allowed));
        assert!(!origin_allowed(Some("https://evil.example.com"), &allowed));
        assert!(origin_allowed(
            Some("https://evil.example.com"),
            &["*".to_string()]
        ));
    }

    #[test]
    fn outbound_queue_full_threshold_closes_connection() {
        let (tx, _rx) = mpsc::channel(1);
        let (close_tx, close_rx) = watch::channel(false);
        let handle = ConnectionHandle::new(ctx(), tx, close_tx, 3);

        assert_eq!(
            handle.send_frame(frame_codec::new_frame(1, 1, Bytes::new())),
            FrameSendResult::Sent
        );
        assert_eq!(
            handle.send_frame(frame_codec::new_frame(1, 2, Bytes::new())),
            FrameSendResult::Dropped
        );
        assert!(!*close_rx.borrow());
        assert_eq!(
            handle.send_frame(frame_codec::new_frame(1, 3, Bytes::new())),
            FrameSendResult::Dropped
        );
        assert!(!*close_rx.borrow());
        assert_eq!(
            handle.send_frame(frame_codec::new_frame(1, 4, Bytes::new())),
            FrameSendResult::Disconnected
        );
        assert!(*close_rx.borrow());
    }

    // ---- R2：pending ack 生命周期（连接局部） ----

    #[test]
    fn tracks_pending_ack_until_acked_and_detects_expiry() {
        let (tx, _rx) = mpsc::channel(8);
        let (close_tx, _close_rx) = watch::channel(false);
        let handle = ConnectionHandle::new(ctx(), tx, close_tx, 3);

        let result = handle.send_push(
            Cmd::MsgPush as u32,
            Bytes::from_static(b"x"),
            true,
            Duration::from_secs(10),
        );
        assert_eq!(result, PushSendResult::Sent);
        assert_eq!(handle.pending_len(), 1);
        assert!(!handle.has_expired_ack(Instant::now()));
        assert!(handle.has_expired_ack(Instant::now() + Duration::from_secs(11)));

        assert!(handle.ack(1)); // 首个 req_id 从 1 开始
        assert!(!handle.ack(1));
        assert!(!handle.ack(0)); // req_id=0 恒为非法
        assert_eq!(handle.pending_len(), 0);
    }

    #[test]
    fn broadcast_send_does_not_track_ack_and_failed_push_rolls_back() {
        let (tx, _rx) = mpsc::channel(1);
        let (close_tx, _close_rx) = watch::channel(false);
        let handle = ConnectionHandle::new(ctx(), tx, close_tx, 99);

        // 广播路径（need_ack=false 预编码帧）不登记 pending。
        assert_eq!(
            handle.send_encoded(Bytes::from_static(b"frame")),
            PushSendResult::Sent
        );
        assert_eq!(handle.pending_len(), 0);

        // 队列满：need_ack 推送失败要回滚 pending 登记。
        assert_eq!(
            handle.send_push(Cmd::MsgPush as u32, Bytes::new(), true, Duration::from_secs(10)),
            PushSendResult::Dropped
        );
        assert_eq!(handle.pending_len(), 0);
    }

    // ---- R5：连接状态机测试（内存收发 + mock 上游） ----

    #[derive(Clone, Default)]
    struct MockUpstream {
        calls: Arc<StdMutex<Vec<String>>>,
        reject_token: bool,
    }

    impl MockUpstream {
        fn calls(&self) -> Vec<String> {
            self.calls.lock().unwrap().clone()
        }

        fn record(&self, name: &str) {
            self.calls.lock().unwrap().push(name.to_string());
        }
    }

    #[async_trait::async_trait]
    impl Upstream for MockUpstream {
        async fn verify_token(&self, _request: VerifyTokenReq) -> Result<VerifyTokenResp> {
            self.record("verify_token");
            if self.reject_token {
                anyhow::bail!("token rejected");
            }
            Ok(VerifyTokenResp {
                code: 0,
                message: "ok".to_string(),
                user_id: 100,
                heartbeat_interval_sec: 30,
                ..Default::default()
            })
        }

        async fn dispatch(
            &self,
            _ctx: ConnCtx,
            _cmd: u32,
            body: Bytes,
            _req_id: u64,
        ) -> Result<UplinkResp> {
            self.record("dispatch");
            Ok(UplinkResp {
                cmd: Cmd::MsgSendAck as u32,
                body, // 原样回显便于断言
            })
        }

        async fn on_connected(&self, _ctx: ConnCtx) -> Result<Empty> {
            self.record("on_connected");
            Ok(Empty {})
        }

        async fn refresh_route(&self, _ctx: ConnCtx) -> Result<Empty> {
            self.record("refresh_route");
            Ok(Empty {})
        }

        async fn on_disconnected(&self, _ctx: ConnCtx) -> Result<Empty> {
            self.record("on_disconnected");
            Ok(Empty {})
        }

        async fn on_push_acked(&self, _ctx: ConnCtx, _ack_body: Bytes) -> Result<Empty> {
            self.record("on_push_acked");
            Ok(Empty {})
        }
    }

    fn test_config() -> Config {
        Config {
            instance_id: "gw-test".to_string(),
            ws_bind: "127.0.0.1:8080".parse().unwrap(),
            upstream_grpc: vec!["http://127.0.0.1:9091".to_string()],
            rabbitmq_url: "amqp://localhost:5672/%2f".to_string(),
            gateway_queue_prefix: "push.gw.".to_string(),
            allowed_origins: vec!["*".to_string()],
            handshake_rate_limit_per_sec: 200,
            handshake_rate_limit_burst: 400,
            per_ip_handshake_rate_limit_per_sec: 20,
            per_ip_handshake_rate_limit_burst: 40,
            per_ip_handshake_limiter_idle_ttl: Duration::from_secs(600),
            auth_timeout: Duration::from_secs(5),
            auth_replay_window: Duration::from_secs(300),
            push_ack_timeout: Duration::from_secs(10),
            dispatch_timeout: Duration::from_secs(10),
            verify_timeout: Duration::from_secs(5),
            conn_event_timeout: Duration::from_secs(5),
            outbound_queue_size: 256,
            outbound_queue_full_disconnect_threshold: 3,
            route_renew_heartbeat_interval: 3,
            drain_timeout: Duration::from_secs(1),
            rabbitmq_prefetch_count: 16,
            min_protocol_version: 1,
            max_frame_bytes: 64 * 1024,
        }
    }

    fn test_state(upstream: MockUpstream) -> AppState {
        AppState {
            config: Arc::new(test_config()),
            rpc: Arc::new(upstream),
            registry: ConnectionRegistry::new(),
            handshake_limiter: HandshakeLimiter::new(200, 400),
            ip_handshake_limiter: IpHandshakeLimiter::new(20, 40, Duration::from_secs(600)),
            lifecycle: Lifecycle::default(),
            metrics: Metrics::default(),
        }
    }

    fn auth_frame() -> Message {
        let body = AuthReq {
            token: "test-token".to_string(),
            tenant_id: 1,
            device_id: "device-a".to_string(),
            platform: 1,
            app_version: "1.0.0".to_string(),
            timestamp: super::unix_ts(),
        }
        .encode_to_vec();
        Message::Binary(frame_codec::encode(&frame_codec::new_frame(
            Cmd::Auth as i32,
            1,
            Bytes::from(body),
        )))
    }

    fn decode_auth_resp(message: &Message) -> AuthResp {
        let Message::Binary(payload) = message else {
            panic!("expected binary frame, got {message:?}");
        };
        let frame = frame_codec::decode_with_limit(payload.clone(), 64 * 1024).unwrap();
        assert_eq!(frame.cmd, Cmd::AuthAck as i32);
        AuthResp::decode(frame.body).unwrap()
    }

    /// 认证成功 → AUTH_ACK(code=0)；上行帧经 Upstream::dispatch 原样回包；
    /// 流结束触发 on_disconnected 清理。
    #[tokio::test]
    async fn authenticates_dispatches_and_cleans_up() {
        let mock = MockUpstream::default();
        let state = test_state(mock.clone());

        let uplink = Message::Binary(frame_codec::encode(&frame_codec::new_frame(
            Cmd::MsgSend as i32,
            7,
            Bytes::from_static(b"hello"),
        )));
        let (mut in_tx, in_rx) = futures::channel::mpsc::unbounded::<Result<Message, axum::Error>>();
        let (out_tx, mut out_rx) = futures::channel::mpsc::unbounded::<Message>();

        in_tx.unbounded_send(Ok(auth_frame())).unwrap();
        in_tx.unbounded_send(Ok(uplink)).unwrap();
        in_tx.disconnect(); // 客户端断开 → read_loop 结束 → 清理

        run_connection(out_tx, in_rx, state.clone()).await.unwrap();

        let auth_ack = decode_auth_resp(&out_rx.try_next().unwrap().unwrap());
        assert_eq!(auth_ack.code, 0);
        assert_eq!(auth_ack.user_id, 100);

        let Message::Binary(payload) = out_rx.try_next().unwrap().unwrap() else {
            panic!("expected dispatch response frame");
        };
        let resp_frame = frame_codec::decode_with_limit(payload, 64 * 1024).unwrap();
        assert_eq!(resp_frame.cmd, Cmd::MsgSendAck as i32);
        assert_eq!(resp_frame.req_id, 7);
        assert_eq!(resp_frame.body.as_ref(), b"hello");

        let calls = mock.calls();
        assert!(calls.contains(&"verify_token".to_string()));
        assert!(calls.contains(&"on_connected".to_string()));
        assert!(calls.contains(&"dispatch".to_string()));
        assert!(calls.contains(&"on_disconnected".to_string()));
        assert_eq!(state.registry.len(), 0);
    }

    /// 重放窗口外的 AUTH → AUTH_ACK(REPLAY_REJECTED)，不触碰上游连接事件。
    #[tokio::test]
    async fn rejects_replayed_auth_without_touching_upstream_conn_events() {
        let mock = MockUpstream::default();
        let state = test_state(mock.clone());

        let stale_auth = {
            let body = AuthReq {
                token: "test-token".to_string(),
                tenant_id: 1,
                device_id: "device-a".to_string(),
                platform: 1,
                app_version: "1.0.0".to_string(),
                timestamp: 1, // 远超重放窗口
            }
            .encode_to_vec();
            Message::Binary(frame_codec::encode(&frame_codec::new_frame(
                Cmd::Auth as i32,
                1,
                Bytes::from(body),
            )))
        };
        let (mut in_tx, in_rx) = futures::channel::mpsc::unbounded::<Result<Message, axum::Error>>();
        let (out_tx, mut out_rx) = futures::channel::mpsc::unbounded::<Message>();
        in_tx.unbounded_send(Ok(stale_auth)).unwrap();
        in_tx.disconnect();

        run_connection(out_tx, in_rx, state).await.unwrap();

        let resp = decode_auth_resp(&out_rx.try_next().unwrap().unwrap());
        assert_eq!(resp.code, super::REPLAY_REJECTED);
        assert!(mock.calls().is_empty());
    }

    /// token 校验失败 → AUTH_ACK(TOKEN_INVALID)，不注册连接。
    #[tokio::test]
    async fn rejects_invalid_token() {
        let mock = MockUpstream {
            reject_token: true,
            ..MockUpstream::default()
        };
        let state = test_state(mock.clone());

        let (mut in_tx, in_rx) = futures::channel::mpsc::unbounded::<Result<Message, axum::Error>>();
        let (out_tx, mut out_rx) = futures::channel::mpsc::unbounded::<Message>();
        in_tx.unbounded_send(Ok(auth_frame())).unwrap();
        in_tx.disconnect();

        run_connection(out_tx, in_rx, state.clone()).await.unwrap();

        let resp = decode_auth_resp(&out_rx.try_next().unwrap().unwrap());
        assert_eq!(resp.code, super::TOKEN_INVALID);
        assert!(!mock.calls().contains(&"on_connected".to_string()));
        assert_eq!(state.registry.len(), 0);
    }

    fn ctx() -> ConnCtx {
        ConnCtx {
            tenant_id: 1,
            user_id: 100,
            platform: 1,
            device_id: "device-a".to_string(),
            conn_id: "conn-a".to_string(),
            gw_instance: "gw-a".to_string(),
            trace_id: "trace-a".to_string(),
        }
    }
}
