use std::{
    sync::{Arc, Mutex},
    time::Instant,
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
        let now = Instant::now();
        let elapsed = now.duration_since(bucket.last_refill).as_secs_f64();
        if elapsed > 0.0 {
            bucket.tokens = (bucket.tokens + elapsed * self.refill_per_sec).min(self.capacity);
            bucket.last_refill = now;
        }
        if bucket.tokens < 1.0 {
            return false;
        }
        bucket.tokens -= 1.0;
        true
    }
}

struct TokenBucket {
    tokens: f64,
    last_refill: Instant,
}

#[cfg(test)]
mod tests {
    use super::HandshakeLimiter;

    #[test]
    fn rejects_when_bucket_is_empty() {
        let limiter = HandshakeLimiter::new(1, 2);

        assert!(limiter.allow());
        assert!(limiter.allow());
        assert!(!limiter.allow());
    }
}
