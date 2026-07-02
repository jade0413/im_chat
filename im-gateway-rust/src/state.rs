use crate::{
    config::Config,
    connection::ConnectionRegistry,
    handshake_limiter::{HandshakeLimiter, IpHandshakeLimiter},
    metrics::Metrics,
    rpc::Upstream,
};
use std::sync::{
    atomic::{AtomicBool, Ordering},
    Arc,
};

#[derive(Clone)]
pub struct AppState {
    pub config: Arc<Config>,
    /// R5：上游依赖抽象为 trait 对象。生产注入 gRPC 实现（RpcClients），
    /// 测试注入内存 mock。网关 QPS 量级下动态分发开销可忽略。
    pub rpc: Arc<dyn Upstream>,
    pub registry: ConnectionRegistry,
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
