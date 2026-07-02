use crate::{
    config::Config,
    proto::im::rpc::v1::{
        conn_event_client::ConnEventClient, gateway_auth_client::GatewayAuthClient,
        uplink_client::UplinkClient, ConnCtx, Empty, PushAckReq, UplinkReq, UplinkResp,
        VerifyTokenReq, VerifyTokenResp,
    },
};
use anyhow::Result;
use async_trait::async_trait;
use bytes::Bytes;
use std::time::Duration;
use tokio::time;
use tonic::transport::{Channel, Endpoint};

/// 网关对上游 Java 服务的全部依赖（R5）。
///
/// 连接状态机（认证/踢线/ack/断连回报）只面向本 trait 编程，
/// 生产实现是 gRPC（`RpcClients`），测试可注入内存 mock，
/// 使核心状态机能够脱离 tonic 做单元/集成测试。
#[async_trait]
pub trait Upstream: Send + Sync + 'static {
    async fn verify_token(&self, request: VerifyTokenReq) -> Result<VerifyTokenResp>;
    async fn dispatch(
        &self,
        ctx: ConnCtx,
        cmd: u32,
        body: Bytes,
        req_id: u64,
    ) -> Result<UplinkResp>;
    async fn on_connected(&self, ctx: ConnCtx) -> Result<Empty>;
    async fn refresh_route(&self, ctx: ConnCtx) -> Result<Empty>;
    async fn on_disconnected(&self, ctx: ConnCtx) -> Result<Empty>;
    async fn on_push_acked(&self, ctx: ConnCtx, ack_body: Bytes) -> Result<Empty>;
}

#[derive(Clone)]
pub struct RpcClients {
    gateway_auth: GatewayAuthClient<Channel>,
    uplink: UplinkClient<Channel>,
    conn_event: ConnEventClient<Channel>,
    timeouts: RpcTimeouts,
}

#[derive(Clone)]
struct RpcTimeouts {
    verify: Duration,
    dispatch: Duration,
    conn_event: Duration,
}

impl RpcClients {
    pub async fn connect(upstreams: &[String], config: &Config) -> Result<Self> {
        let endpoints = upstreams
            .iter()
            .map(|addr| Endpoint::from_shared(addr.clone()))
            .collect::<Result<Vec<_>, _>>()?;
        // R3：单端点保持启动即连（fail-fast，行为与旧版一致）；
        // 多端点走 tower p2c 负载均衡，打散到多个 Java 实例，
        // 消除单 TCP 连接的吞吐上限与 TCP 层队头阻塞。
        let channel = match endpoints.len() {
            0 => anyhow::bail!("no upstream grpc endpoint configured"),
            1 => {
                endpoints
                    .into_iter()
                    .next()
                    .expect("one endpoint")
                    .connect()
                    .await?
            }
            _ => Channel::balance_list(endpoints.into_iter()),
        };
        Ok(Self {
            gateway_auth: GatewayAuthClient::new(channel.clone()),
            uplink: UplinkClient::new(channel.clone()),
            conn_event: ConnEventClient::new(channel),
            timeouts: RpcTimeouts {
                verify: config.verify_timeout,
                dispatch: config.dispatch_timeout,
                conn_event: config.conn_event_timeout,
            },
        })
    }
}

#[async_trait]
impl Upstream for RpcClients {
    async fn verify_token(&self, request: VerifyTokenReq) -> Result<VerifyTokenResp> {
        let mut client = self.gateway_auth.clone();
        Ok(
            time::timeout(self.timeouts.verify, client.verify_token(request))
                .await??
                .into_inner(),
        )
    }

    async fn dispatch(
        &self,
        ctx: ConnCtx,
        cmd: u32,
        body: Bytes,
        req_id: u64,
    ) -> Result<UplinkResp> {
        let mut client = self.uplink.clone();
        Ok(time::timeout(
            self.timeouts.dispatch,
            client.dispatch(UplinkReq {
                ctx: Some(ctx),
                cmd,
                body,
                req_id,
            }),
        )
        .await??
        .into_inner())
    }

    async fn on_connected(&self, ctx: ConnCtx) -> Result<Empty> {
        let mut client = self.conn_event.clone();
        Ok(
            time::timeout(self.timeouts.conn_event, client.on_connected(ctx))
                .await??
                .into_inner(),
        )
    }

    async fn refresh_route(&self, ctx: ConnCtx) -> Result<Empty> {
        let mut client = self.conn_event.clone();
        Ok(
            time::timeout(self.timeouts.conn_event, client.refresh_route(ctx))
                .await??
                .into_inner(),
        )
    }

    async fn on_disconnected(&self, ctx: ConnCtx) -> Result<Empty> {
        let mut client = self.conn_event.clone();
        Ok(
            time::timeout(self.timeouts.conn_event, client.on_disconnected(ctx))
                .await??
                .into_inner(),
        )
    }

    async fn on_push_acked(&self, ctx: ConnCtx, ack_body: Bytes) -> Result<Empty> {
        let mut client = self.conn_event.clone();
        Ok(time::timeout(
            self.timeouts.conn_event,
            client.on_push_acked(PushAckReq {
                ctx: Some(ctx),
                ack_body,
            }),
        )
        .await??
        .into_inner())
    }
}
