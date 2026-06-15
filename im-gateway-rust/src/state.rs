use crate::{
    config::Config,
    connection::{ConnectionRegistry, PendingAcks},
    handshake_limiter::{HandshakeLimiter, IpHandshakeLimiter},
    metrics::Metrics,
    rpc::RpcClients,
};
use std::sync::{
    atomic::{AtomicBool, Ordering},
    Arc,
};

#[derive(Clone)]
pub struct AppState {
    pub config: Arc<Config>,
    pub rpc: RpcClients,
    pub registry: ConnectionRegistry,
    pub pending_acks: PendingAcks,
    pub handshake_limiter: HandshakeLimiter,
    pub ip_handshake_limiter: IpHandshakeLimiter,
    pub lifecycle: Lifecycle,
    pub metrics: Metrics,
}

#[derive(Clone, Default)]
pub struct Lifecycle {
    draining: Arc<AtomicBool>,
}

impl Lifecycle {
    pub fn is_ready(&self) -> bool {
        !self.draining.load(Ordering::Relaxed)
    }

    pub fn start_draining(&self) {
        self.draining.store(true, Ordering::Relaxed);
    }
}
