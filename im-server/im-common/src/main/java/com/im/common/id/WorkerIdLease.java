package com.im.common.id;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.redis.RedisKeys;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "im.id.lease", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkerIdLease implements InitializingBean, DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(WorkerIdLease.class);

  private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>("""
      if redis.call('get', KEYS[1]) == ARGV[1] then
        return redis.call('pexpire', KEYS[1], ARGV[2])
      end
      return 0
      """, Long.class);

  private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
      if redis.call('get', KEYS[1]) == ARGV[1] then
        return redis.call('del', KEYS[1])
      end
      return 0
      """, Long.class);

  private final StringRedisTemplate redisTemplate;
  private final long workerId;
  private final Duration ttl;
  private final Duration renewInterval;
  private final String instanceId = UUID.randomUUID().toString();
  private ScheduledExecutorService executor;

  public WorkerIdLease(StringRedisTemplate redisTemplate,
      @Value("${im.id.worker-id:1}") long workerId,
      @Value("${im.id.lease.ttl:30s}") Duration ttl,
      @Value("${im.id.lease.renew-interval:10s}") Duration renewInterval) {
    this.redisTemplate = redisTemplate;
    this.workerId = workerId;
    this.ttl = normalize(ttl, Duration.ofSeconds(30));
    this.renewInterval = normalize(renewInterval, Duration.ofSeconds(10));
  }

  @Override
  public void afterPropertiesSet() {
    String key = key();
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, instanceId, ttl);
    if (!Boolean.TRUE.equals(acquired)) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "snowflake worker_id lease conflict: " + workerId);
    }
    executor = Executors.newSingleThreadScheduledExecutor(
        Thread.ofVirtual().name("worker-id-lease-", 0).factory());
    executor.scheduleWithFixedDelay(this::renewSafely,
        renewInterval.toMillis(),
        renewInterval.toMillis(),
        TimeUnit.MILLISECONDS);
    log.info("snowflake worker_id lease acquired, worker_id={}", workerId);
  }

  @Override
  public void destroy() {
    if (executor != null) {
      executor.shutdownNow();
    }
    redisTemplate.execute(RELEASE_SCRIPT, List.of(key()), instanceId);
  }

  private void renewSafely() {
    try {
      Long renewed = redisTemplate.execute(RENEW_SCRIPT, List.of(key()),
          instanceId, Long.toString(ttl.toMillis()));
      if (!Long.valueOf(1L).equals(renewed)) {
        log.error("snowflake worker_id lease lost, worker_id={}", workerId);
      }
    } catch (Exception ex) {
      log.error("snowflake worker_id lease renew failed, worker_id={}", workerId, ex);
    }
  }

  private String key() {
    return RedisKeys.workerIdLease(workerId);
  }

  private Duration normalize(Duration value, Duration fallback) {
    return value == null || value.isZero() || value.isNegative() ? fallback : value;
  }
}
