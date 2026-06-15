package com.im.message.service;

import com.im.common.tenant.TenantContext;
import com.im.message.dao.mapper.MessageRetentionMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

/**
 * 消息保留清理作业（架构审查 P2-5）。
 *
 * <p>{@code message} 单表全租户共用，过去无清理作业会无限增长。本服务按
 * {@code tenant_config.msg_retention_days} 周期性批量删除过期消息，删除走
 * {@code idx_tenant_created}（V9 迁移新增）。
 *
 * <p>沿用 {@code OutboxPoller} 的 SmartLifecycle + 虚拟线程后台循环模式，不引入 Spring @Scheduled。
 * 每租户工作包在 {@code TenantContext.runWithTenant} 内执行，满足 MyBatis-Plus 租户拦截器约束；
 * 单租户失败不影响其它租户与下一轮。
 *
 * <p>安全阀：保留天数必须 ≥ {@code MIN_RETENTION_DAYS}，避免误配 0/负数导致整库清空；
 * 每租户每轮最多删除 {@code maxBatchesPerCycle} 批，避免长时间占用 DB。
 */
@Service
public class MessageRetentionService implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(MessageRetentionService.class);

  /** 保留天数安全下限：低于此值视为误配，跳过该租户（不清理）。 */
  private static final int MIN_RETENTION_DAYS = 1;

  private final MessageRetentionMapper retentionMapper;
  private final Clock clock;
  private final boolean enabled;
  private final long scanIntervalMillis;
  private final int batchSize;
  private final int maxBatchesPerCycle;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private ExecutorService executor;

  @Autowired
  public MessageRetentionService(
      MessageRetentionMapper retentionMapper,
      @Value("${im.message.retention.enabled:false}") boolean enabled,
      @Value("${im.message.retention.scan-interval-millis:3600000}") long scanIntervalMillis,
      @Value("${im.message.retention.batch-size:1000}") int batchSize,
      @Value("${im.message.retention.max-batches-per-cycle:50}") int maxBatchesPerCycle) {
    this(retentionMapper, Clock.systemUTC(), enabled, scanIntervalMillis, batchSize,
        maxBatchesPerCycle);
  }

  MessageRetentionService(MessageRetentionMapper retentionMapper, Clock clock, boolean enabled,
      long scanIntervalMillis, int batchSize, int maxBatchesPerCycle) {
    this.retentionMapper = retentionMapper;
    this.clock = clock;
    this.enabled = enabled;
    this.scanIntervalMillis = Math.max(60_000L, scanIntervalMillis);
    this.batchSize = Math.max(1, batchSize);
    this.maxBatchesPerCycle = Math.max(1, maxBatchesPerCycle);
  }

  @Override
  public void start() {
    if (!enabled || !running.compareAndSet(false, true)) {
      return;
    }
    executor = Executors.newVirtualThreadPerTaskExecutor();
    executor.submit(this::loop);
    log.info("message retention purge started, interval_ms={}, batch_size={}, max_batches={}",
        scanIntervalMillis, batchSize, maxBatchesPerCycle);
  }

  @Override
  public void stop() {
    if (!running.compareAndSet(true, false)) {
      return;
    }
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  @Override
  public boolean isAutoStartup() {
    return true;
  }

  private void loop() {
    while (running.get()) {
      try {
        purgeOnce();
      } catch (Exception ex) {
        log.warn("message retention cycle failed", ex);
      }
      sleepInterval();
    }
  }

  private void sleepInterval() {
    try {
      Thread.sleep(scanIntervalMillis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      running.set(false);
    }
  }

  /** 跑一轮：枚举活跃租户，逐租户清理。返回本轮删除总行数（便于测试/观测）。 */
  public int purgeOnce() {
    List<Long> tenantIds = retentionMapper.activeTenantIds();
    int total = 0;
    for (Long tenantId : tenantIds) {
      if (tenantId == null || tenantId <= 0) {
        continue;
      }
      try {
        total += purgeTenant(tenantId);
      } catch (Exception ex) {
        log.warn("message retention purge failed for tenant_id={}", tenantId, ex);
      }
    }
    return total;
  }

  private int purgeTenant(long tenantId) {
    final int[] deleted = {0};
    TenantContext.runWithTenant(tenantId, () -> {
      Integer days = retentionMapper.retentionDays(tenantId);
      if (days == null || days < MIN_RETENTION_DAYS) {
        return; // 未配置或误配（含 0/负数）→ 跳过，绝不在此误删
      }
      LocalDateTime cutoff = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
          .minusDays(days);
      int removed = 0;
      for (int i = 0; i < maxBatchesPerCycle; i++) {
        int n = retentionMapper.purgeOlderThan(tenantId, cutoff, batchSize);
        removed += n;
        if (n < batchSize) {
          break; // 本租户已无更多过期消息
        }
      }
      if (removed > 0) {
        log.info("message retention purged tenant_id={}, retention_days={}, removed={}",
            tenantId, days, removed);
      }
      deleted[0] = removed;
    });
    return deleted[0];
  }
}
