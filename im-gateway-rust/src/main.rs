mod config;
mod connection;
mod frame_codec;
mod handshake_limiter;
mod metrics;
mod proto;
mod push;
mod rpc;
mod state;

use crate::{
    config::Config,
    connection::{ConnectionRegistry, PendingAcks},
    handshake_limiter::{HandshakeLimiter, IpHandshakeLimiter},
    metrics::Metrics,
    rpc::RpcClients,
    state::{AppState, Lifecycle},
};
use anyhow::Result;
use axum::{http::StatusCode, routing::get, Router};
use std::sync::Arc;
use tokio::{net::TcpListener, time};
use tracing::info;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

#[tokio::main]
async fn main() -> Result<()> {
    init_tracing();

    let config = Arc::new(Config::from_env()?);
    let rpc = RpcClients::connect(config.upstream_grpc.clone(), config.as_ref()).await?;
    let state = AppState {
        config: config.clone(),
        rpc,
        registry: ConnectionRegistry::new(),
        pending_acks: PendingAcks::new(),
        handshake_limiter: HandshakeLimiter::new(
            config.handshake_rate_limit_per_sec,
            config.handshake_rate_limit_burst,
        ),
        ip_handshake_limiter: IpHandshakeLimiter::new(
            config.per_ip_handshake_rate_limit_per_sec,
            config.per_ip_handshake_rate_limit_burst,
            config.per_ip_handshake_limiter_idle_ttl,
        ),
        lifecycle: Lifecycle::default(),
        metrics: Metrics::default(),
    };

    let push_state = state.clone();
    let push_task = tokio::spawn(async move {
        push::run_push_consumer_forever(push_state).await;
    });

    let app = Router::new()
        .route("/health", get(health))
        .route("/ready", get(ready))
        .route("/metrics", get(metrics))
        .route("/ws", get(connection::ws_handler))
        .with_state(state.clone());

    let listener = TcpListener::bind(config.ws_bind).await?;
    let queue_name = config.push_queue_name();
    info!(
        bind = %config.ws_bind,
        instance = config.instance_id.as_str(),
        upstream = config.upstream_grpc.as_str(),
        queue = queue_name.as_str(),
        "im gateway started"
    );
    axum::serve(
        listener,
        app.into_make_service_with_connect_info::<std::net::SocketAddr>(),
    )
    .with_graceful_shutdown(shutdown_signal(state.clone()))
    .await?;
    push_task.abort();
    Ok(())
}

async fn health() -> &'static str {
    "OK"
}

async fn ready(axum::extract::State(state): axum::extract::State<AppState>) -> StatusCode {
    if state.lifecycle.is_ready() {
        StatusCode::OK
    } else {
        StatusCode::SERVICE_UNAVAILABLE
    }
}

async fn metrics(axum::extract::State(state): axum::extract::State<AppState>) -> String {
    state
        .metrics
        .render_prometheus(state.registry.len(), state.pending_acks.len())
}

async fn shutdown_signal(state: AppState) {
    let ctrl_c = async {
        tokio::signal::ctrl_c()
            .await
            .expect("failed to install ctrl+c handler");
    };

    #[cfg(unix)]
    let terminate = async {
        tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
            .expect("failed to install signal handler")
            .recv()
            .await;
    };

    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => {},
        _ = terminate => {},
    }
    state.lifecycle.start_draining();
    let closed = state.registry.close_all();
    info!(
        drain_timeout_ms = state.config.drain_timeout.as_millis(),
        closed, "gateway entering drain mode"
    );
    time::sleep(state.config.drain_timeout).await;
}

fn init_tracing() {
    tracing_subscriber::registry()
        .with(EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info")))
        .with(tracing_subscriber::fmt::layer())
        .init();
}
