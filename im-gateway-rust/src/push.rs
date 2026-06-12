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
use tracing::{info, warn};

pub async fn run_push_consumer(state: AppState) -> Result<()> {
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
        ack_timeout: state.config.push_ack_timeout,
    };
    let mut delivered = 0usize;
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
            if is_kick {
                handle.close();
            }
        }
    }
    info!(
        tenant_id = envelope.tenant_id,
        cmd = envelope.cmd,
        targets = target_count,
        delivered,
        need_ack = envelope.need_ack,
        trace_id = envelope.trace_id,
        "push envelope handled"
    );
    Ok(())
}
