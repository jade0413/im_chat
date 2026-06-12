use anyhow::{Context, Result};
use std::{env, net::SocketAddr, time::Duration};

#[derive(Clone, Debug)]
pub struct Config {
    pub instance_id: String,
    pub ws_bind: SocketAddr,
    pub upstream_grpc: String,
    pub rabbitmq_url: String,
    pub gateway_queue_prefix: String,
    pub auth_timeout: Duration,
    pub auth_replay_window: Duration,
    pub push_ack_timeout: Duration,
    pub dispatch_timeout: Duration,
    pub verify_timeout: Duration,
    pub conn_event_timeout: Duration,
    pub outbound_queue_size: usize,
    pub outbound_queue_full_disconnect_threshold: u64,
    pub route_renew_heartbeat_interval: u64,
    pub min_protocol_version: u32,
}

impl Config {
    pub fn from_env() -> Result<Self> {
        Ok(Self {
            instance_id: read_env(&["IM_GATEWAY_INSTANCE_ID", "GW_INSTANCE_ID"], "gw-local"),
            ws_bind: read_env(&["IM_GATEWAY_WS_BIND", "GW_WS_BIND"], "0.0.0.0:8080")
                .parse()
                .context("invalid gateway bind address")?,
            upstream_grpc: read_env(
                &["IM_GATEWAY_UPSTREAM_GRPC", "UPSTREAM_GRPC"],
                "http://127.0.0.1:9091",
            ),
            rabbitmq_url: read_env(
                &["IM_GATEWAY_RABBITMQ_URL", "RABBITMQ_URL"],
                "amqp://im:im_dev_mq_pwd@127.0.0.1:5672/%2f",
            ),
            gateway_queue_prefix: read_env(
                &["IM_PUSH_GATEWAY_QUEUE_PREFIX", "GW_PUSH_QUEUE_PREFIX"],
                "push.gw.",
            ),
            auth_timeout: read_duration_secs(&["IM_GATEWAY_AUTH_TIMEOUT_SEC"], 5),
            auth_replay_window: read_duration_secs(&["IM_GATEWAY_AUTH_REPLAY_WINDOW_SEC"], 300),
            push_ack_timeout: read_duration_secs(&["IM_GATEWAY_PUSH_ACK_TIMEOUT_SEC"], 10),
            dispatch_timeout: read_duration_secs(&["IM_GATEWAY_DISPATCH_TIMEOUT_SEC"], 10),
            verify_timeout: read_duration_secs(&["IM_GATEWAY_VERIFY_TIMEOUT_SEC"], 5),
            conn_event_timeout: read_duration_secs(&["IM_GATEWAY_CONN_EVENT_TIMEOUT_SEC"], 5),
            outbound_queue_size: read_env(&["IM_GATEWAY_OUTBOUND_QUEUE_SIZE"], "256")
                .parse()
                .context("invalid outbound queue size")?,
            outbound_queue_full_disconnect_threshold: read_env(
                &["IM_GATEWAY_OUTBOUND_QUEUE_FULL_THRESHOLD"],
                "3",
            )
            .parse()
            .context("invalid outbound queue full threshold")?,
            route_renew_heartbeat_interval: read_env(&["IM_GATEWAY_ROUTE_RENEW_HEARTBEATS"], "3")
                .parse()
                .context("invalid route renew heartbeat interval")?,
            min_protocol_version: read_env(&["IM_GATEWAY_MIN_PROTOCOL_VERSION"], "1")
                .parse()
                .context("invalid min protocol version")?,
        })
    }

    pub fn push_queue_name(&self) -> String {
        format!("{}{}", self.gateway_queue_prefix, self.instance_id)
    }
}

fn read_env(keys: &[&str], default: &str) -> String {
    keys.iter()
        .find_map(|key| env::var(key).ok().filter(|value| !value.is_empty()))
        .unwrap_or_else(|| default.to_string())
}

fn read_duration_secs(keys: &[&str], default_secs: u64) -> Duration {
    let secs = keys
        .iter()
        .find_map(|key| env::var(key).ok())
        .and_then(|value| value.parse::<u64>().ok())
        .unwrap_or(default_secs);
    Duration::from_secs(secs)
}

#[cfg(test)]
mod tests {
    use super::Config;

    #[test]
    fn derives_push_queue_from_prefix_and_instance() {
        let config = Config {
            instance_id: "gw-a".to_string(),
            ws_bind: "127.0.0.1:8080".parse().unwrap(),
            upstream_grpc: "http://127.0.0.1:9091".to_string(),
            rabbitmq_url: "amqp://localhost:5672/%2f".to_string(),
            gateway_queue_prefix: "push.gw.".to_string(),
            auth_timeout: std::time::Duration::from_secs(5),
            auth_replay_window: std::time::Duration::from_secs(300),
            push_ack_timeout: std::time::Duration::from_secs(10),
            dispatch_timeout: std::time::Duration::from_secs(10),
            verify_timeout: std::time::Duration::from_secs(5),
            conn_event_timeout: std::time::Duration::from_secs(5),
            outbound_queue_size: 256,
            outbound_queue_full_disconnect_threshold: 3,
            route_renew_heartbeat_interval: 3,
            min_protocol_version: 1,
        };

        assert_eq!(config.push_queue_name(), "push.gw.gw-a");
    }
}
