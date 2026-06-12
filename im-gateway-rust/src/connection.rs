use crate::{
    frame_codec,
    metrics::Metrics,
    proto::im::{
        rpc::v1::{ConnCtx, VerifyTokenReq},
        ws::v1::{AuthReq, AuthResp, Cmd, Frame, KickNotify},
    },
    rpc::RpcClients,
    state::AppState,
};
use anyhow::{Context, Result};
use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        State,
    },
    response::IntoResponse,
};
use dashmap::DashMap;
use futures_util::{stream::SplitStream, SinkExt, StreamExt};
use prost::Message as _;
use std::{
    hash::{Hash, Hasher},
    sync::{
        atomic::{AtomicU64, Ordering},
        Arc,
    },
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use tokio::{
    sync::{mpsc, watch},
    time,
};
use tracing::{debug, info, warn};
use uuid::Uuid;

// Connection-layer codes mirrored from common/error.proto and im-common ErrorCode.
const OK: i32 = 0;
const TOKEN_INVALID: i32 = 1001;
const REPLAY_REJECTED: i32 = 1005;
const INTERNAL_ERROR: i32 = 9999;
const PROTO_TOO_OLD_REASON: i32 = 4;

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
        }
    }

    pub fn ctx(&self) -> ConnCtx {
        self.ctx.clone()
    }

    pub fn key(&self) -> ConnKey {
        self.key.clone()
    }

    pub fn send_push(
        &self,
        cmd: u32,
        body: Vec<u8>,
        need_ack: bool,
        runtime: PushSendRuntime,
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
        let frame = frame_codec::new_frame(cmd as i32, req_id, body);
        match self.send_frame(frame) {
            FrameSendResult::Sent => {}
            FrameSendResult::Dropped => return PushSendResult::Dropped,
            FrameSendResult::Disconnected => return PushSendResult::Disconnected,
        }
        if need_ack {
            runtime.pending_acks.track(
                self.clone(),
                req_id,
                runtime.registry,
                runtime.rpc,
                runtime.metrics,
                runtime.ack_timeout,
            );
        }
        PushSendResult::Sent
    }

    fn send_frame(&self, frame: Frame) -> FrameSendResult {
        match self.sender.try_send(Outbound::Frame(frame)) {
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

#[derive(Clone)]
pub struct PushSendRuntime {
    pub pending_acks: PendingAcks,
    pub registry: ConnectionRegistry,
    pub rpc: RpcClients,
    pub metrics: Metrics,
    pub ack_timeout: Duration,
}

enum Outbound {
    Frame(Frame),
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum FrameSendResult {
    Sent,
    Dropped,
    Disconnected,
}

#[derive(Clone, Debug, Eq, PartialEq, Hash)]
struct AckKey {
    conn: ConnKey,
    req_id: u64,
}

#[derive(Clone)]
pub struct PendingAcks {
    inner: Arc<DashMap<AckKey, ()>>,
}

impl PendingAcks {
    pub fn new() -> Self {
        Self {
            inner: Arc::new(DashMap::new()),
        }
    }

    fn track(
        &self,
        handle: ConnectionHandle,
        req_id: u64,
        registry: ConnectionRegistry,
        rpc: RpcClients,
        metrics: Metrics,
        ack_timeout: Duration,
    ) {
        let ack_key = AckKey {
            conn: handle.key(),
            req_id,
        };
        self.inner.insert(ack_key.clone(), ());

        let pending_acks = self.clone();
        tokio::spawn(async move {
            time::sleep(ack_timeout).await;
            if pending_acks.inner.remove(&ack_key).is_none() {
                return;
            }
            warn!(
                tenant_id = handle.ctx.tenant_id,
                user_id = handle.ctx.user_id,
                platform = handle.ctx.platform,
                conn_id = handle.ctx.conn_id,
                req_id,
                "push ack timeout, close connection"
            );
            if registry.remove(&handle.key()).is_some() {
                metrics.connection_closed(handle.ctx.tenant_id);
                metrics.ack_timeout_disconnect();
                handle.close();
                if let Err(err) = rpc.on_disconnected(handle.ctx()).await {
                    warn!(?err, "failed to report disconnected after push ack timeout");
                }
            }
        });
    }

    pub fn ack(&self, conn: &ConnKey, req_id: u64) -> bool {
        if req_id == 0 {
            return false;
        }
        self.inner
            .remove(&AckKey {
                conn: conn.clone(),
                req_id,
            })
            .is_some()
    }

    pub fn cancel_connection(&self, conn: &ConnKey) {
        self.inner.retain(|key, _| &key.conn != conn);
    }
}

pub async fn ws_handler(State(state): State<AppState>, ws: WebSocketUpgrade) -> impl IntoResponse {
    ws.on_upgrade(move |socket| handle_socket(socket, state))
}

async fn handle_socket(socket: WebSocket, state: AppState) {
    if let Err(err) = handle_socket_inner(socket, state).await {
        warn!(?err, "websocket connection closed with error");
    }
}

async fn handle_socket_inner(socket: WebSocket, state: AppState) -> Result<()> {
    let (mut ws_sender, mut ws_receiver) = socket.split();
    let first_frame = read_auth_frame(&mut ws_receiver, state.config.auth_timeout).await?;
    if first_frame.version < state.config.min_protocol_version {
        send_kick(&mut ws_sender, PROTO_TOO_OLD_REASON, "protocol too old").await?;
        return Ok(());
    }
    if first_frame.cmd != Cmd::Auth as i32 {
        send_auth_resp(&mut ws_sender, TOKEN_INVALID, "AUTH is required", 0, 0).await?;
        return Ok(());
    }

    let auth = AuthReq::decode(first_frame.body.as_slice()).context("invalid auth body")?;
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
            warn!(?err, "verify token rpc failed");
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

    let (outbound_tx, mut outbound_rx) = mpsc::channel(state.config.outbound_queue_size);
    let (close_tx, mut close_rx) = watch::channel(false);
    let handle = ConnectionHandle::new(
        ctx.clone(),
        outbound_tx,
        close_tx,
        state.config.outbound_queue_full_disconnect_threshold,
    );
    let key = handle.key();
    state.registry.insert(handle.clone());

    if let Err(err) = state.rpc.on_connected(ctx.clone()).await {
        warn!(?err, "on_connected rpc failed");
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
        "websocket authenticated"
    );

    let writer = tokio::spawn(async move {
        loop {
            tokio::select! {
                outbound = outbound_rx.recv() => {
                    let Some(Outbound::Frame(frame)) = outbound else {
                        break;
                    };
                    if ws_sender
                        .send(Message::Binary(frame_codec::encode(&frame)))
                        .await
                        .is_err()
                    {
                        break;
                    }
                }
                changed = close_rx.changed() => {
                    if changed.is_ok() && *close_rx.borrow() {
                        let _ = ws_sender.send(Message::Close(None)).await;
                    }
                    break;
                }
            }
        }
    });

    let heartbeat_interval = normalize_heartbeat_interval(verify.heartbeat_interval_sec);
    let read_result = read_loop(
        &mut ws_receiver,
        ctx.clone(),
        key.clone(),
        state.clone(),
        heartbeat_interval,
    )
    .await;
    state.pending_acks.cancel_connection(&key);
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

async fn read_loop(
    receiver: &mut SplitStream<WebSocket>,
    ctx: ConnCtx,
    key: ConnKey,
    state: AppState,
    heartbeat_interval: Duration,
) -> Result<()> {
    let idle_timeout = heartbeat_interval * 3;
    while let Ok(message) = time::timeout(idle_timeout, receiver.next()).await {
        let Some(message) = message else {
            break;
        };
        let message = message?;
        let payload = match message {
            Message::Binary(payload) => payload,
            Message::Close(_) => break,
            _ => continue,
        };
        let frame = frame_codec::decode(&payload)?;
        if frame.version < state.config.min_protocol_version {
            if let Some(handle) = state.registry.get(
                key.tenant_id,
                key.user_id,
                key.platform,
                key.conn_id.as_str(),
            ) {
                let body = KickNotify {
                    reason: PROTO_TOO_OLD_REASON,
                    message: "protocol too old".to_string(),
                }
                .encode_to_vec();
                handle.send_frame(frame_codec::new_frame(Cmd::Kick as i32, 0, body));
                handle.close();
            }
            break;
        }
        match frame.cmd {
            cmd if cmd == Cmd::Ping as i32 => {
                if let Some(handle) = state.registry.get(
                    key.tenant_id,
                    key.user_id,
                    key.platform,
                    key.conn_id.as_str(),
                ) {
                    handle.send_frame(frame_codec::new_frame(
                        Cmd::Pong as i32,
                        frame.req_id,
                        Vec::new(),
                    ));
                    if handle.should_refresh_route(state.config.route_renew_heartbeat_interval) {
                        let rpc = state.rpc.clone();
                        let heartbeat_ctx = ctx.clone();
                        tokio::spawn(async move {
                            if let Err(err) = rpc.on_connected(heartbeat_ctx).await {
                                warn!(?err, "failed to refresh route on heartbeat");
                            }
                        });
                    }
                }
            }
            cmd if cmd == Cmd::MsgRecvAck as i32 => {
                let acked = state.pending_acks.ack(&key, frame.req_id);
                debug!(
                    tenant_id = ctx.tenant_id,
                    user_id = ctx.user_id,
                    conn_id = ctx.conn_id,
                    req_id = frame.req_id,
                    acked,
                    "received push ack"
                );
                if let Err(err) = state.rpc.on_push_acked(ctx.clone(), frame.body).await {
                    warn!(?err, "on_push_acked rpc failed");
                }
            }
            cmd if cmd == Cmd::Auth as i32 => {
                warn!("duplicate AUTH frame, close connection");
                break;
            }
            cmd if cmd >= 0 => {
                let cmd = cmd as u32;
                state.metrics.uplink_frame(cmd);
                match state
                    .rpc
                    .dispatch(ctx.clone(), cmd, frame.body, frame.req_id)
                    .await
                {
                    Ok(response) => {
                        if let Some(handle) = state.registry.get(
                            key.tenant_id,
                            key.user_id,
                            key.platform,
                            key.conn_id.as_str(),
                        ) {
                            handle.send_frame(frame_codec::new_frame(
                                response.cmd as i32,
                                frame.req_id,
                                response.body,
                            ));
                        }
                    }
                    Err(err) => {
                        warn!(
                            ?err,
                            tenant_id = ctx.tenant_id,
                            user_id = ctx.user_id,
                            conn_id = ctx.conn_id,
                            cmd,
                            req_id = frame.req_id,
                            "dispatch failed"
                        );
                        if let Some(handle) = state.registry.get(
                            key.tenant_id,
                            key.user_id,
                            key.platform,
                            key.conn_id.as_str(),
                        ) {
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

async fn read_auth_frame(
    receiver: &mut SplitStream<WebSocket>,
    timeout: Duration,
) -> Result<Frame> {
    let next = time::timeout(timeout, receiver.next())
        .await
        .context("AUTH timeout")?
        .context("websocket closed before AUTH")??;
    match next {
        Message::Binary(payload) => frame_codec::decode(&payload),
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
    S: futures_util::Sink<Message, Error = axum::Error> + Unpin,
{
    let body = AuthResp {
        code,
        message: message.to_string(),
        user_id,
        server_ts: unix_ts(),
        heartbeat_interval_sec,
    }
    .encode_to_vec();
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
    S: futures_util::Sink<Message, Error = axum::Error> + Unpin,
{
    let body = KickNotify {
        reason,
        message: message.to_string(),
    }
    .encode_to_vec();
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

#[cfg(test)]
mod tests {
    use super::{
        frame_codec, normalize_heartbeat_interval, timestamp_in_window, ConnCtx, ConnectionHandle,
        FrameSendResult,
    };
    use std::time::Duration;
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
    fn outbound_queue_full_threshold_closes_connection() {
        let (tx, _rx) = mpsc::channel(1);
        let (close_tx, close_rx) = watch::channel(false);
        let handle = ConnectionHandle::new(ctx(), tx, close_tx, 3);

        assert_eq!(
            handle.send_frame(frame_codec::new_frame(1, 1, Vec::new())),
            FrameSendResult::Sent
        );
        assert_eq!(
            handle.send_frame(frame_codec::new_frame(1, 2, Vec::new())),
            FrameSendResult::Dropped
        );
        assert!(!*close_rx.borrow());
        assert_eq!(
            handle.send_frame(frame_codec::new_frame(1, 3, Vec::new())),
            FrameSendResult::Dropped
        );
        assert!(!*close_rx.borrow());
        assert_eq!(
            handle.send_frame(frame_codec::new_frame(1, 4, Vec::new())),
            FrameSendResult::Disconnected
        );
        assert!(*close_rx.borrow());
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
