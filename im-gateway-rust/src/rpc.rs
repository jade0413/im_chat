use crate::{
    config::Config,
    proto::im::rpc::v1::{
        conn_event_client::ConnEventClient, gateway_auth_client::GatewayAuthClient,
        uplink_client::UplinkClient, ConnCtx, Empty, PushAckReq, UplinkReq, UplinkResp,
        VerifyTokenReq, VerifyTokenResp,
    },
};
use anyhow::Result;
use std::time::Duration;
use tokio::time;
use tonic::transport::Channel;

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
    pub async fn connect(upstream_grpc: String, config: &Config) -> Result<Self> {
        let channel = Channel::from_shared(upstream_grpc)?.connect().await?;
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

    pub async fn verify_token(&self, request: VerifyTokenReq) -> Result<VerifyTokenResp> {
        let mut client = self.gateway_auth.clone();
        Ok(
            time::timeout(self.timeouts.verify, client.verify_token(request))
                .await??
                .into_inner(),
        )
    }

    pub async fn dispatch(
        &self,
        ctx: ConnCtx,
        cmd: u32,
        body: Vec<u8>,
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

    pub async fn on_connected(&self, ctx: ConnCtx) -> Result<Empty> {
        let mut client = self.conn_event.clone();
        Ok(
            time::timeout(self.timeouts.conn_event, client.on_connected(ctx))
                .await??
                .into_inner(),
        )
    }

    pub async fn on_disconnected(&self, ctx: ConnCtx) -> Result<Empty> {
        let mut client = self.conn_event.clone();
        Ok(
            time::timeout(self.timeouts.conn_event, client.on_disconnected(ctx))
                .await??
                .into_inner(),
        )
    }

    pub async fn on_push_acked(&self, ctx: ConnCtx, ack_body: Vec<u8>) -> Result<Empty> {
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
