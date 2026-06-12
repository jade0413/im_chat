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
            min_protocol_version: 1,
        };

        assert_eq!(config.push_queue_name(), "push.gw.gw-a");
    }
}
