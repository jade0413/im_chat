use crate::{
    connection::PushSendResult,
    frame_codec,
    proto::im::{rpc::v1::PushEnvelope, ws::v1::Cmd},
    state::AppState,
};
use anyhow::{Context, Result};
use bytes::Bytes;
use futures_util::StreamExt;
use lapin::{
    options::{BasicAckOptions, BasicConsumeOptions, BasicQosOptions, QueueDeclareOptions},
    types::FieldTable,
    Connection, ConnectionProperties,
};
use prost::Message as _;
use std::time::Instant;
use tokio::time::{sleep, Duration};
use tracing::{debug, info, warn};

const INITIAL_RECONNECT_BACKOFF: Duration = Duration::from_secs(1);
const MAX_RECONNECT_BACKOFF: Duration = Duration::from_secs(30);
const STABLE_CONSUMER_RESET_AFTER: Duration = Duration::from_secs(60);

pub async fn run_push_consumer_forever(state: AppState) {
    let mut backoff = INITIAL_RECONNECT_BACKOFF;
    loop {
        let started_at = Instant::now();
        match run_push_consumer_once(state.clone()).await {
            Ok(()) => warn!("gateway push consumer ended, reconnecting"),
            Err(err) => warn!(?err, "gateway push consumer failed, reconnecting"),
        }
        let (delay, next_backoff) = reconnect_backoff(backoff, started_at.elapsed());
        sleep(delay).await;
        backoff = next_backoff;
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
        .basic_qos(
            state.config.rabbitmq_prefetch_count.max(1),
            BasicQosOptions::default(),
        )
        .await
        .context("failed to configure rabbitmq prefetch")?;

    channel
        .queue_declare(
            &queue_name,
            QueueDeclareOptions {
                durable: true,
                exclusive: false,
                auto_delete: false,
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
        let mut delivery = match delivery {
            Ok(delivery) => delivery,
            Err(err) => {
                warn!(?err, "rabbitmq delivery error");
                continue;
            }
        };
        // Vec -> Bytes 零拷贝转让所有权，后续 envelope.body 是它的引用计数切片（R1）。
        let payload = Bytes::from(std::mem::take(&mut delivery.data));
        if let Err(err) = handle_push_delivery(&state, payload).await {
            warn!(?err, "failed to handle push envelope");
        }
        delivery
            .ack(BasicAckOptions::default())
            .await
            .context("failed to ack push delivery")?;
    }
    Ok(())
}

async fn handle_push_delivery(state: &AppState, payload: Bytes) -> Result<()> {
    let envelope = PushEnvelope::decode(payload).context("invalid push envelope")?;
    if envelope.cmd > i32::MAX as u32 {
        warn!(cmd = envelope.cmd, "skip push envelope with invalid cmd");
        return Ok(());
    }
    let is_kick = envelope.cmd == Cmd::Kick as u32;
    let target_count = envelope.targets.len();
    // R1：广播路径（need_ack=false，req_id 恒 0）所有目标帧字节完全相同，
    // 整只信封只编码一次，target 间共享（Bytes clone = 引用计数 +1）。
    // need_ack 路径 req_id 逐连接分配，帧需逐个编码，但 body 仍零拷贝共享。
    let shared_frame: Option<Bytes> = (!envelope.need_ack).then(|| {
        frame_codec::encode(&frame_codec::new_frame(
            envelope.cmd as i32,
            0,
            envelope.body.clone(),
        ))
    });
    let mut delivered = 0usize;
    let mut failed = 0usize;
    for target in &envelope.targets {
        let Some(handle) = state.registry.get(
            envelope.tenant_id,
            target.user_id,
            target.platform,
            target.conn_id.as_str(),
        ) else {
            continue;
        };
        let result = match &shared_frame {
            Some(frame_bytes) => handle.send_encoded(frame_bytes.clone()),
            None => handle.send_push(
                envelope.cmd,
                envelope.body.clone(),
                true,
                state.config.push_ack_timeout,
            ),
        };
        match result {
            PushSendResult::Sent => {
                delivered += 1;
                state.metrics.push_delivered();
                if is_kick {
                    handle.close();
                }
            }
            PushSendResult::Dropped => {
                failed += 1;
                state.metrics.push_failed();
                if is_kick {
                    disconnect_slow_consumer(state, &handle, envelope.tenant_id);
                }
            }
            PushSendResult::Disconnected => {
                failed += 1;
                state.metrics.push_failed();
                disconnect_slow_consumer(state, &handle, envelope.tenant_id);
            }
        }
    }
    // P3：每条下行都打日志在高吞吐下量很大，降到 debug；异常路径仍各自 warn。
    debug!(
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

fn disconnect_slow_consumer(
    state: &AppState,
    handle: &crate::connection::ConnectionHandle,
    tenant_id: i64,
) {
    handle.close();
    if state.registry.remove(&handle.key()).is_some() {
        state.metrics.connection_closed(tenant_id);
        state.metrics.slow_consumer_disconnect();
        // C-7：断连回报 gRPC spawn 出去——本地 remove/close 已同步生效，
        // 不让一次慢消费者断连阻塞整条下行消费管线一个 gRPC 往返。
        let rpc = state.rpc.clone();
        let ctx = handle.ctx();
        tokio::spawn(async move {
            if let Err(err) = rpc.on_disconnected(ctx).await {
                warn!(?err, "failed to report disconnected for slow consumer");
            }
        });
    }
}

fn reconnect_backoff(current: Duration, consumer_run_duration: Duration) -> (Duration, Duration) {
    let delay = if consumer_run_duration >= STABLE_CONSUMER_RESET_AFTER {
        INITIAL_RECONNECT_BACKOFF
    } else {
        current
    };
    (delay, (delay * 2).min(MAX_RECONNECT_BACKOFF))
}

#[cfg(test)]
mod tests {
    use super::{reconnect_backoff, INITIAL_RECONNECT_BACKOFF, MAX_RECONNECT_BACKOFF};
    use tokio::time::Duration;

    #[test]
    fn reconnect_backoff_grows_until_max_for_unstable_consumer() {
        let (delay, next) = reconnect_backoff(Duration::from_secs(4), Duration::from_secs(10));

        assert_eq!(delay, Duration::from_secs(4));
        assert_eq!(next, Duration::from_secs(8));

        let (delay, next) = reconnect_backoff(Duration::from_secs(30), Duration::from_secs(10));
        assert_eq!(delay, MAX_RECONNECT_BACKOFF);
        assert_eq!(next, MAX_RECONNECT_BACKOFF);
    }

    #[test]
    fn reconnect_backoff_resets_after_stable_consumer_run() {
        let (delay, next) = reconnect_backoff(Duration::from_secs(30), Duration::from_secs(60));

        assert_eq!(delay, INITIAL_RECONNECT_BACKOFF);
        assert_eq!(next, Duration::from_secs(2));
    }
}
