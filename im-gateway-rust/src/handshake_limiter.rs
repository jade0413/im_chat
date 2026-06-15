use dashmap::DashMap;
use std::{
    net::IpAddr,
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};

#[derive(Clone)]
pub struct HandshakeLimiter {
    inner: Arc<Mutex<TokenBucket>>,
    refill_per_sec: f64,
    capacity: f64,
}

impl HandshakeLimiter {
    pub fn new(refill_per_sec: u32, capacity: u32) -> Self {
        let refill_per_sec = f64::from(refill_per_sec.max(1));
        let capacity = f64::from(capacity.max(1));
        Self {
            inner: Arc::new(Mutex::new(TokenBucket {
                tokens: capacity,
                last_refill: Instant::now(),
            })),
            refill_per_sec,
            capacity,
        }
    }

    pub fn allow(&self) -> bool {
        let mut bucket = self.inner.lock().expect("handshake limiter poisoned");
        allow_bucket(&mut bucket, self.refill_per_sec, self.capacity)
    }
}

#[derive(Clone)]
pub struct IpHandshakeLimiter {
    buckets: Arc<DashMap<IpAddr, Mutex<TokenBucket>>>,
    refill_per_sec: f64,
    capacity: f64,
    idle_ttl: Duration,
}

impl IpHandshakeLimiter {
    pub fn new(refill_per_sec: u32, capacity: u32, idle_ttl: Duration) -> Self {
        let refill_per_sec = f64::from(refill_per_sec.max(1));
        let capacity = f64::from(capacity.max(1));
        Self {
            buckets: Arc::new(DashMap::new()),
            refill_per_sec,
            capacity,
            idle_ttl: idle_ttl.max(Duration::from_secs(60)),
        }
    }

    pub fn allow(&self, ip: IpAddr) -> bool {
        self.cleanup_idle();
        let entry = self.buckets.entry(ip).or_insert_with(|| {
            Mutex::new(TokenBucket {
                tokens: self.capacity,
                last_refill: Instant::now(),
            })
        });
        let mut bucket = entry
            .lock()
            .expect("per-ip handshake limiter bucket poisoned");
        allow_bucket(&mut bucket, self.refill_per_sec, self.capacity)
    }

    fn cleanup_idle(&self) {
        let idle_ttl = self.idle_ttl;
        self.buckets.retain(|_, bucket| {
            let Ok(bucket) = bucket.get_mut() else {
                return false;
            };
            bucket.last_refill.elapsed() <= idle_ttl
        });
    }
}

struct TokenBucket {
    tokens: f64,
    last_refill: Instant,
}

fn allow_bucket(bucket: &mut TokenBucket, refill_per_sec: f64, capacity: f64) -> bool {
    let now = Instant::now();
    let elapsed = now.duration_since(bucket.last_refill).as_secs_f64();
    if elapsed > 0.0 {
        bucket.tokens = (bucket.tokens + elapsed * refill_per_sec).min(capacity);
        bucket.last_refill = now;
    }
    if bucket.tokens < 1.0 {
        return false;
    }
    bucket.tokens -= 1.0;
    true
}

#[cfg(test)]
mod tests {
    use super::{HandshakeLimiter, IpHandshakeLimiter};
    use std::{net::IpAddr, time::Duration};

    #[test]
    fn rejects_when_bucket_is_empty() {
        let limiter = HandshakeLimiter::new(1, 2);

        assert!(limiter.allow());
        assert!(limiter.allow());
        assert!(!limiter.allow());
    }

    #[test]
    fn per_ip_limiter_isolated_by_source_ip() {
        let limiter = IpHandshakeLimiter::new(1, 1, Duration::from_secs(60));
        let ip_a = IpAddr::from([10, 0, 0, 1]);
        let ip_b = IpAddr::from([10, 0, 0, 2]);

        assert!(limiter.allow(ip_a));
        assert!(!limiter.allow(ip_a));
        assert!(limiter.allow(ip_b));
    }
}
