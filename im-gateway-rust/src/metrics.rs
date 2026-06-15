use dashmap::DashMap;
use std::sync::{
    atomic::{AtomicI64, AtomicU64, Ordering},
    Arc,
};

#[derive(Clone, Default)]
pub struct Metrics {
    online_connections: Arc<DashMap<i64, AtomicI64>>,
    uplink_frames: Arc<DashMap<u32, AtomicU64>>,
    push_delivered: Arc<AtomicU64>,
    push_failed: Arc<AtomicU64>,
    ack_timeout_disconnects: Arc<AtomicU64>,
    slow_consumer_disconnects: Arc<AtomicU64>,
    handshake_rejected_draining: Arc<AtomicU64>,
    handshake_rejected_rate_limit: Arc<AtomicU64>,
    handshake_rejected_per_ip_rate_limit: Arc<AtomicU64>,
    handshake_rejected_origin: Arc<AtomicU64>,
    dispatch_ok: Arc<AtomicU64>,
    dispatch_failed: Arc<AtomicU64>,
    dispatch_duration_ms_sum: Arc<AtomicU64>,
}

impl Metrics {
    pub fn connection_opened(&self, tenant_id: i64) {
        self.online_connections
            .entry(tenant_id)
            .or_insert_with(|| AtomicI64::new(0))
            .fetch_add(1, Ordering::Relaxed);
    }

    pub fn connection_closed(&self, tenant_id: i64) {
        self.online_connections
            .entry(tenant_id)
            .or_insert_with(|| AtomicI64::new(0))
            .fetch_update(Ordering::Relaxed, Ordering::Relaxed, |value| {
                Some(value.saturating_sub(1))
            })
            .ok();
    }

    pub fn uplink_frame(&self, cmd: u32) {
        self.uplink_frames
            .entry(cmd)
            .or_insert_with(|| AtomicU64::new(0))
            .fetch_add(1, Ordering::Relaxed);
    }

    pub fn push_delivered(&self) {
        self.push_delivered.fetch_add(1, Ordering::Relaxed);
    }

    pub fn push_failed(&self) {
        self.push_failed.fetch_add(1, Ordering::Relaxed);
    }

    pub fn ack_timeout_disconnect(&self) {
        self.ack_timeout_disconnects.fetch_add(1, Ordering::Relaxed);
    }

    pub fn slow_consumer_disconnect(&self) {
        self.slow_consumer_disconnects
            .fetch_add(1, Ordering::Relaxed);
    }

    pub fn handshake_rejected_draining(&self) {
        self.handshake_rejected_draining
            .fetch_add(1, Ordering::Relaxed);
    }

    pub fn handshake_rejected_rate_limit(&self) {
        self.handshake_rejected_rate_limit
            .fetch_add(1, Ordering::Relaxed);
    }

    pub fn handshake_rejected_per_ip_rate_limit(&self) {
        self.handshake_rejected_per_ip_rate_limit
            .fetch_add(1, Ordering::Relaxed);
    }

    pub fn handshake_rejected_origin(&self) {
        self.handshake_rejected_origin
            .fetch_add(1, Ordering::Relaxed);
    }

    pub fn dispatch_completed(&self, ok: bool, duration_ms: u64) {
        if ok {
            self.dispatch_ok.fetch_add(1, Ordering::Relaxed);
        } else {
            self.dispatch_failed.fetch_add(1, Ordering::Relaxed);
        }
        self.dispatch_duration_ms_sum
            .fetch_add(duration_ms, Ordering::Relaxed);
    }

    pub fn render_prometheus(&self, registry_connections: usize, pending_acks: usize) -> String {
        let mut output = String::new();
        output.push_str("# TYPE im_gateway_registry_connections gauge\n");
        output.push_str(&format!(
            "im_gateway_registry_connections {}\n",
            registry_connections
        ));
        output.push_str("# TYPE im_gateway_pending_acks gauge\n");
        output.push_str(&format!("im_gateway_pending_acks {}\n", pending_acks));
        output.push_str("# TYPE im_gateway_online_connections gauge\n");
        let mut tenants = self
            .online_connections
            .iter()
            .map(|entry| (*entry.key(), entry.value().load(Ordering::Relaxed)))
            .collect::<Vec<_>>();
        tenants.sort_by_key(|(tenant_id, _)| *tenant_id);
        for (tenant_id, value) in tenants {
            output.push_str(&format!(
                "im_gateway_online_connections{{tenant_id=\"{}\"}} {}\n",
                tenant_id, value
            ));
        }

        output.push_str("# TYPE im_gateway_uplink_frames_total counter\n");
        let mut cmds = self
            .uplink_frames
            .iter()
            .map(|entry| (*entry.key(), entry.value().load(Ordering::Relaxed)))
            .collect::<Vec<_>>();
        cmds.sort_by_key(|(cmd, _)| *cmd);
        for (cmd, value) in cmds {
            output.push_str(&format!(
                "im_gateway_uplink_frames_total{{cmd=\"{}\"}} {}\n",
                cmd, value
            ));
        }

        output.push_str("# TYPE im_gateway_push_delivered_total counter\n");
        output.push_str(&format!(
            "im_gateway_push_delivered_total {}\n",
            self.push_delivered.load(Ordering::Relaxed)
        ));
        output.push_str("# TYPE im_gateway_push_failed_total counter\n");
        output.push_str(&format!(
            "im_gateway_push_failed_total {}\n",
            self.push_failed.load(Ordering::Relaxed)
        ));
        output.push_str("# TYPE im_gateway_ack_timeout_disconnects_total counter\n");
        output.push_str(&format!(
            "im_gateway_ack_timeout_disconnects_total {}\n",
            self.ack_timeout_disconnects.load(Ordering::Relaxed)
        ));
        output.push_str("# TYPE im_gateway_slow_consumer_disconnects_total counter\n");
        output.push_str(&format!(
            "im_gateway_slow_consumer_disconnects_total {}\n",
            self.slow_consumer_disconnects.load(Ordering::Relaxed)
        ));
        output.push_str("# TYPE im_gateway_handshake_rejected_total counter\n");
        output.push_str(&format!(
            "im_gateway_handshake_rejected_total{{reason=\"draining\"}} {}\n",
            self.handshake_rejected_draining.load(Ordering::Relaxed)
        ));
        output.push_str(&format!(
            "im_gateway_handshake_rejected_total{{reason=\"instance_rate_limit\"}} {}\n",
            self.handshake_rejected_rate_limit.load(Ordering::Relaxed)
        ));
        output.push_str(&format!(
            "im_gateway_handshake_rejected_total{{reason=\"per_ip_rate_limit\"}} {}\n",
            self.handshake_rejected_per_ip_rate_limit
                .load(Ordering::Relaxed)
        ));
        output.push_str(&format!(
            "im_gateway_handshake_rejected_total{{reason=\"origin\"}} {}\n",
            self.handshake_rejected_origin.load(Ordering::Relaxed)
        ));
        output.push_str("# TYPE im_gateway_dispatch_total counter\n");
        output.push_str(&format!(
            "im_gateway_dispatch_total{{result=\"ok\"}} {}\n",
            self.dispatch_ok.load(Ordering::Relaxed)
        ));
        output.push_str(&format!(
            "im_gateway_dispatch_total{{result=\"failed\"}} {}\n",
            self.dispatch_failed.load(Ordering::Relaxed)
        ));
        output.push_str("# TYPE im_gateway_dispatch_duration_ms_sum counter\n");
        output.push_str(&format!(
            "im_gateway_dispatch_duration_ms_sum {}\n",
            self.dispatch_duration_ms_sum.load(Ordering::Relaxed)
        ));
        output
    }
}

#[cfg(test)]
mod tests {
    use super::Metrics;

    #[test]
    fn renders_prometheus_metrics() {
        let metrics = Metrics::default();
        metrics.connection_opened(1);
        metrics.uplink_frame(10);
        metrics.push_delivered();
        metrics.push_failed();
        metrics.ack_timeout_disconnect();
        metrics.handshake_rejected_draining();
        metrics.handshake_rejected_rate_limit();
        metrics.handshake_rejected_per_ip_rate_limit();
        metrics.handshake_rejected_origin();
        metrics.dispatch_completed(true, 12);
        metrics.dispatch_completed(false, 7);

        let rendered = metrics.render_prometheus(1, 2);
        assert!(rendered.contains("im_gateway_registry_connections 1"));
        assert!(rendered.contains("im_gateway_pending_acks 2"));
        assert!(rendered.contains("im_gateway_online_connections{tenant_id=\"1\"} 1"));
        assert!(rendered.contains("im_gateway_uplink_frames_total{cmd=\"10\"} 1"));
        assert!(rendered.contains("im_gateway_push_delivered_total 1"));
        assert!(rendered.contains("im_gateway_push_failed_total 1"));
        assert!(rendered.contains("im_gateway_ack_timeout_disconnects_total 1"));
        assert!(rendered.contains("im_gateway_handshake_rejected_total{reason=\"draining\"} 1"));
        assert!(rendered.contains("im_gateway_dispatch_total{result=\"ok\"} 1"));
        assert!(rendered.contains("im_gateway_dispatch_total{result=\"failed\"} 1"));
        assert!(rendered.contains("im_gateway_dispatch_duration_ms_sum 19"));
    }
}
