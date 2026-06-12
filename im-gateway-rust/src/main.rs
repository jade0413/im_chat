mod config;
mod connection;
mod frame_codec;
mod proto;
mod push;
mod rpc;
mod state;

use crate::{
    config::Config,
    connection::{ConnectionRegistry, PendingAcks},
    rpc::RpcClients,
    state::AppState,
};
use anyhow::Result;
use axum::{routing::get, Router};
use std::sync::Arc;
use tokio::net::TcpListener;
use tracing::{error, info};
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

#[tokio::main]
async fn main() -> Result<()> {
    init_tracing();

    let config = Arc::new(Config::from_env()?);
    let rpc = RpcClients::connect(config.upstream_grpc.clone()).await?;
    let state = AppState {
        config: config.clone(),
        rpc,
        registry: ConnectionRegistry::new(),
        pending_acks: PendingAcks::new(),
    };

    let push_state = state.clone();
    let push_task = tokio::spawn(async move {
        if let Err(err) = push::run_push_consumer(push_state).await {
            error!(?err, "push consumer stopped");
        }
    });

    let app = Router::new()
        .route("/health", get(health))
        .route("/ws", get(connection::ws_handler))
        .with_state(state);

    let listener = TcpListener::bind(config.ws_bind).await?;
    let queue_name = config.push_queue_name();
    info!(
        bind = %config.ws_bind,
        instance = config.instance_id.as_str(),
        upstream = config.upstream_grpc.as_str(),
        queue = queue_name.as_str(),
        "im gateway started"
    );
    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await?;
    push_task.abort();
    Ok(())
}

async fn health() -> &'static str {
    "OK"
}

async fn shutdown_signal() {
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
}

fn init_tracing() {
    tracing_subscriber::registry()
        .with(EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info")))
        .with(tracing_subscriber::fmt::layer())
        .init();
}
