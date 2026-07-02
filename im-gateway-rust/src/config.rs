use crate::frame_codec::DEFAULT_MAX_FRAME_BYTES;
use anyhow::{Context, Result};
use std::{env, net::SocketAddr, time::Duration};

#[derive(Clone, Debug)]
pub struct Config {
    pub instance_id: String,
    pub ws_bind: SocketAddr,
    /// 上游 Java gRPC 地址，支持逗号分隔多端点（R3：多实例负载均衡）。
    /// 单地址 = 现状行为；多地址 = tower p2c balance。
    pub upstream_grpc: Vec<String>,
    pub rabbitmq_url: String,
    pub gateway_queue_prefix: String,
    pub allowed_origins: Vec<String>,
    pub handshake_rate_limit_per_sec: u32,
    pub handshake_rate_limit_burst: u32,
    pub per_ip_handshake_rate_limit_per_sec: u32,
    pub per_ip_handshake_rate_limit_burst: u32,
    pub per_ip_handshake_limiter_idle_ttl: Duration,
    pub auth_timeout: Duration,
    pub auth_replay_window: Duration,
    pub push_ack_timeout: Duration,
    pub dispatch_timeout: Duration,
    pub verify_timeout: Duration,
    pub conn_event_timeout: Duration,
    pub outbound_queue_size: usize,
    pub outbound_queue_full_disconnect_threshold: u64,
    pub route_renew_heartbeat_interval: u64,
    pub drain_timeout: Duration,
    pub rabbitmq_prefetch_count: u16,
    pub min_protocol_version: u32,
    /// 单帧最大字节数，超过则拒绝解码（防恶意超大帧 OOM）
    pub max_frame_bytes: usize,
}

impl Config {
    pub fn from_env() -> Result<Self> {
        Ok(Self {
            instance_id: read_env(&["IM_GATEWAY_INSTANCE_ID", "GW_INSTANCE_ID"], "gw-local"),
            ws_bind: read_env(&["IM_GATEWAY_WS_BIND", "GW_WS_BIND"], "0.0.0.0:8080")
                .parse()
                .context("invalid gateway bind address")?,
            upstream_grpc: read_csv_env(
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
            allowed_origins: read_csv_env(&["IM_GATEWAY_ALLOWED_ORIGINS"], "*"),
            handshake_rate_limit_per_sec: read_env(
                &["IM_GATEWAY_HANDSHAKE_RATE_LIMIT_PER_SEC"],
                "200",
            )
            .parse()
            .context("invalid handshake rate limit per second")?,
            handshake_rate_limit_burst: read_env(&["IM_GATEWAY_HANDSHAKE_RATE_LIMIT_BURST"], "400")
                .parse()
                .context("invalid handshake rate limit burst")?,
            per_ip_handshake_rate_limit_per_sec: read_env(
                &["IM_GATEWAY_PER_IP_HANDSHAKE_RATE_LIMIT_PER_SEC"],
                "20",
            )
            .parse()
            .context("invalid per-ip handshake rate limit per second")?,
            per_ip_handshake_rate_limit_burst: read_env(
                &["IM_GATEWAY_PER_IP_HANDSHAKE_RATE_LIMIT_BURST"],
                "40",
            )
            .parse()
            .context("invalid per-ip handshake rate limit burst")?,
            per_ip_handshake_limiter_idle_ttl: read_duration_secs(
                &["IM_GATEWAY_PER_IP_HANDSHAKE_LIMITER_IDLE_TTL_SEC"],
                600,
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
            drain_timeout: read_duration_secs(&["IM_GATEWAY_DRAIN_TIMEOUT_SEC"], 10),
            rabbitmq_prefetch_count: read_env(&["IM_GATEWAY_RABBITMQ_PREFETCH_COUNT"], "256")
                .parse()
                .context("invalid rabbitmq prefetch count")?,
            min_protocol_version: read_env(&["IM_GATEWAY_MIN_PROTOCOL_VERSION"], "1")
                .parse()
                .context("invalid min protocol version")?,
            max_frame_bytes: read_env(
                &["IM_GATEWAY_MAX_FRAME_BYTES"],
                &DEFAULT_MAX_FRAME_BYTES.to_string(),
            )
            .parse()
            .context("invalid max frame bytes")?,
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

fn read_csv_env(keys: &[&str], default: &str) -> Vec<String> {
    read_env(keys, default)
        .split(',')
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(ToOwned::to_owned)
        .collect()
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
            upstream_grpc: vec!["http://127.0.0.1:9091".to_string()],
            rabbitmq_url: "amqp://localhost:5672/%2f".to_string(),
            gateway_queue_prefix: "push.gw.".to_string(),
            allowed_origins: vec!["*".to_string()],
            handshake_rate_limit_per_sec: 200,
            handshake_rate_limit_burst: 400,
            per_ip_handshake_rate_limit_per_sec: 20,
            per_ip_handshake_rate_limit_burst: 40,
            per_ip_handshake_limiter_idle_ttl: std::time::Duration::from_secs(600),
            auth_timeout: std::time::Duration::from_secs(5),
            auth_replay_window: std::time::Duration::from_secs(300),
            push_ack_timeout: std::time::Duration::from_secs(10),
            dispatch_timeout: std::time::Duration::from_secs(10),
            verify_timeout: std::time::Duration::from_secs(5),
            conn_event_timeout: std::time::Duration::from_secs(5),
            outbound_queue_size: 256,
            outbound_queue_full_disconnect_threshold: 3,
            route_renew_heartbeat_interval: 3,
            drain_timeout: std::time::Duration::from_secs(10),
            rabbitmq_prefetch_count: 256,
            min_protocol_version: 1,
            max_frame_bytes: 65536,
        };

        assert_eq!(config.push_queue_name(), "push.gw.gw-a");
    }
}
