use crate::{
    config::Config,
    connection::{ConnectionRegistry, PendingAcks},
    handshake_limiter::HandshakeLimiter,
    metrics::Metrics,
    rpc::RpcClients,
};
use std::sync::Arc;

#[derive(Clone)]
pub struct AppState {
    pub config: Arc<Config>,
    pub rpc: RpcClients,
    pub registry: ConnectionRegistry,
    pub pending_acks: PendingAcks,
    pub handshake_limiter: HandshakeLimiter,
    pub metrics: Metrics,
}
