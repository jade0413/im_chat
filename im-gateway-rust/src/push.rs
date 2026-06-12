use crate::{
    connection::PushSendRuntime,
    proto::im::{rpc::v1::PushEnvelope, ws::v1::Cmd},
    state::AppState,
};
use anyhow::{Context, Result};
use futures_util::StreamExt;
use lapin::{
    options::{BasicAckOptions, BasicConsumeOptions, QueueDeclareOptions},
    types::FieldTable,
    Connection, ConnectionProperties,
};
use prost::Message as _;
use tokio::time::{sleep, Duration};
use tracing::{info, warn};

pub async fn run_push_consumer_forever(state: AppState) {
    let mut backoff = Duration::from_secs(1);
    loop {
        match run_push_consumer_once(state.clone()).await {
            Ok(()) => warn!("gateway push consumer ended, reconnecting"),
            Err(err) => warn!(?err, "gateway push consumer failed, reconnecting"),
        }
        sleep(backoff).await;
        backoff = (backoff * 2).min(Duration::from_secs(30));
    }
}

async fn run_push_consumer_once(state: AppState) -> Result<()> {
    let queue_name = state.config.push_queue_name();
    let connection =
        Connection::connect(&state.config.rabbitmq_url, ConnectionProperties::default())
            .await
            .context("failed to connect rabbitmq")?;
    let channel = connection
        .create_channel()
        .await
        .context("failed to create rabbitmq channel")?;

    channel
        .queue_declare(
            &queue_name,
            QueueDeclareOptions {
                durable: false,
                exclusive: false,
                auto_delete: true,
                ..QueueDeclareOptions::default()
            },
            FieldTable::default(),
        )
        .await
        .context("failed to declare gateway push queue")?;

    let mut consumer = channel
        .basic_consume(
            &queue_name,
            &format!("{}-consumer", state.config.instance_id),
            BasicConsumeOptions::default(),
            FieldTable::default(),
        )
        .await
        .context("failed to consume gateway push queue")?;

    info!(queue = queue_name, "gateway push consumer started");
    while let Some(delivery) = consumer.next().await {
        let delivery = match delivery {
            Ok(delivery) => delivery,
            Err(err) => {
                warn!(?err, "rabbitmq delivery error");
                continue;
            }
        };
        if let Err(err) = handle_push_delivery(&state, delivery.data.as_ref()).await {
            warn!(?err, "failed to handle push envelope");
        }
        delivery
            .ack(BasicAckOptions::default())
            .await
            .context("failed to ack push delivery")?;
    }
    Ok(())
}

async fn handle_push_delivery(state: &AppState, payload: &[u8]) -> Result<()> {
    let envelope = PushEnvelope::decode(payload).context("invalid push envelope")?;
    let is_kick = envelope.cmd == Cmd::Kick as u32;
    let target_count = envelope.targets.len();
    let runtime = PushSendRuntime {
        pending_acks: state.pending_acks.clone(),
        registry: state.registry.clone(),
        rpc: state.rpc.clone(),
        metrics: state.metrics.clone(),
        ack_timeout: state.config.push_ack_timeout,
    };
    let mut delivered = 0usize;
    let mut failed = 0usize;
    for target in envelope.targets {
        let Some(handle) = state.registry.get(
            envelope.tenant_id,
            target.user_id,
            target.platform,
            target.conn_id.as_str(),
        ) else {
            continue;
        };
        if handle.send_push(
            envelope.cmd,
            envelope.body.clone(),
            envelope.need_ack,
            runtime.clone(),
        ) {
            delivered += 1;
            state.metrics.push_delivered();
            if is_kick {
                handle.close();
            }
        } else {
            failed += 1;
            state.metrics.push_failed();
            handle.close();
            if state.registry.remove(&handle.key()).is_some() {
                state.metrics.connection_closed(envelope.tenant_id);
                if let Err(err) = state.rpc.on_disconnected(handle.ctx()).await {
                    warn!(?err, "failed to report disconnected for slow consumer");
                }
            }
        }
    }
    info!(
        tenant_id = envelope.tenant_id,
        cmd = envelope.cmd,
        targets = target_count,
        delivered,
        failed,
        need_ack = envelope.need_ack,
        trace_id = envelope.trace_id,
        "push envelope handled"
    );
    Ok(())
}
