use crate::{
    config::Config,
    connection::{ConnectionRegistry, PendingAcks},
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
    pub metrics: Metrics,
}
