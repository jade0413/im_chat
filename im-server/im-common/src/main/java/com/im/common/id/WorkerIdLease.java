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

/**
 * Snowflake worker_id 分布式租约（Redis SetNX + 自动续期）。
 *
 * <p>启动时若 worker_id 已被占用，自动读取剩余 TTL：
 * <ul>
 *   <li>若剩余 TTL ≤ {@code im.id.lease.max-wait}（默认 15s），等待过期后重试——
 *       覆盖开发阶段因 SIGKILL 未释放租约的常见场景。</li>
 *   <li>否则抛异常（说明另一个实例正在运行，真正的冲突）。</li>
 * </ul>
 */
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
  private final Duration maxWait;
  private final String instanceId = UUID.randomUUID().toString();
  private ScheduledExecutorService executor;

  public WorkerIdLease(StringRedisTemplate redisTemplate,
      @Value("${im.id.worker-id:1}") long workerId,
      @Value("${im.id.lease.ttl:12s}") Duration ttl,
      @Value("${im.id.lease.renew-interval:4s}") Duration renewInterval,
      @Value("${im.id.lease.max-wait:15s}") Duration maxWait) {
    this.redisTemplate = redisTemplate;
    this.workerId = workerId;
    this.ttl = normalize(ttl, Duration.ofSeconds(12));
    this.renewInterval = normalize(renewInterval, Duration.ofSeconds(4));
    this.maxWait = normalize(maxWait, Duration.ofSeconds(15));
  }

  @Override
  public void afterPropertiesSet() throws InterruptedException {
    acquireWithRetry();
    startRenewTask();
    log.info("snowflake worker_id lease acquired, worker_id={}", workerId);
  }

  @Override
  public void destroy() {
    if (executor != null) {
      executor.shutdownNow();
    }
    redisTemplate.execute(RELEASE_SCRIPT, List.of(key()), instanceId);
    log.info("snowflake worker_id lease released, worker_id={}", workerId);
  }

  // ── 私有方法 ──────────────────────────────────────────────────────────────

  /**
   * 尝试获取租约。若被占用且剩余 TTL ≤ maxWait，等待过期后重试一次。
   * 这覆盖了开发阶段 SIGKILL 未能释放租约的场景（旧租约最多等 12s 自然过期）。
   */
  private void acquireWithRetry() throws InterruptedException {
    if (tryAcquire()) {
      return;
    }

    // 查剩余 TTL（ms）
    Long remainingMs = redisTemplate.getExpire(key(), TimeUnit.MILLISECONDS);
    long remaining = remainingMs == null || remainingMs < 0 ? 0L : remainingMs;

    if (remaining > 0 && remaining <= maxWait.toMillis()) {
      log.warn("snowflake worker_id={} lease occupied, remaining TTL={}ms ≤ maxWait={}ms, waiting…",
          workerId, remaining, maxWait.toMillis());
      Thread.sleep(remaining + 200); // 多等 200ms 留余量
      if (tryAcquire()) {
        return;
      }
    }

    throw new ImException(ErrorCode.INTERNAL_ERROR,
        "snowflake worker_id lease conflict: " + workerId
        + "，若确认无其他实例运行，可等待 TTL 过期（" + ttl.toSeconds() + "s）后重启");
  }

  private boolean tryAcquire() {
    return Boolean.TRUE.equals(
        redisTemplate.opsForValue().setIfAbsent(key(), instanceId, ttl));
  }

  private void startRenewTask() {
    executor = Executors.newSingleThreadScheduledExecutor(
        Thread.ofVirtual().name("worker-id-lease-", 0).factory());
    executor.scheduleWithFixedDelay(this::renewSafely,
        renewInterval.toMillis(),
        renewInterval.toMillis(),
        TimeUnit.MILLISECONDS);
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

  private static Duration normalize(Duration value, Duration fallback) {
    return value == null || value.isZero() || value.isNegative() ? fallback : value;
  }
}
