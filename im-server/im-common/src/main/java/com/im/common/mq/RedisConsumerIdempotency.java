package com.im.common.mq;

import com.im.common.redis.RedisKeys;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisConsumerIdempotency {

  private static final Duration DEFAULT_TTL = Duration.ofHours(24);

  private final StringRedisTemplate redisTemplate;

  public RedisConsumerIdempotency(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public boolean tryMarkEvent(String namespace, long tenantId, long eventId, Duration ttl) {
    if (eventId <= 0) {
      return false;
    }
    return tryMark(namespace, tenantId, Long.toString(eventId), ttl);
  }

  public boolean tryMarkBusinessKey(String namespace, long tenantId, String businessKey,
      Duration ttl) {
    return tryMark(namespace, tenantId, businessKey, ttl);
  }

  private boolean tryMark(String namespace, long tenantId, String key, Duration ttl) {
    Boolean marked = redisTemplate.opsForValue()
        .setIfAbsent(RedisKeys.consumerDedup(namespace, tenantId, key), "1", normalize(ttl));
    return Boolean.TRUE.equals(marked);
  }

  private Duration normalize(Duration ttl) {
    return ttl == null || ttl.isZero() || ttl.isNegative() ? DEFAULT_TTL : ttl;
  }
}
