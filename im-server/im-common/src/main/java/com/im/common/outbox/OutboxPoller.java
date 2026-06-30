package com.im.common.outbox;

import com.im.common.mq.RabbitMqEvent;
import com.im.common.mq.RabbitMqPublisher;
import com.im.common.outbox.dao.entity.OutboxEntity;
import com.im.common.outbox.dao.mapper.CommonOutboxMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

@Service
public class OutboxPoller implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

  private final CommonOutboxMapper outboxMapper;
  private final RabbitMqPublisher publisher;
  private final OutboxProperties properties;
  private final Clock clock;
  private final String claimOwner;
  private final AtomicBoolean running = new AtomicBoolean(false);
  // 提交后即时发布信号：write 成功提交即 release，poller 立刻醒来发布，
  // 避免每条消息白等一个轮询间隔（D-1）。轮询仍作为漏发/失败兜底。
  private final Semaphore wakeups = new Semaphore(0);
  private ExecutorService executor;

  @Autowired
  public OutboxPoller(CommonOutboxMapper outboxMapper,
      RabbitMqPublisher publisher,
      OutboxProperties properties) {
    this(outboxMapper, publisher, properties, Clock.systemUTC(), defaultClaimOwner());
  }

  OutboxPoller(CommonOutboxMapper outboxMapper,
      RabbitMqPublisher publisher,
      OutboxProperties properties,
      Clock clock,
      String claimOwner) {
    this.outboxMapper = outboxMapper;
    this.publisher = publisher;
    this.properties = properties;
    this.clock = clock;
    this.claimOwner = claimOwner;
  }

  @Override
  public void start() {
    if (!properties.isEnabled() || !running.compareAndSet(false, true)) {
      return;
    }
    executor = Executors.newVirtualThreadPerTaskExecutor();
    executor.submit(this::pollLoop);
    log.info("outbox poller started, batch_size={}", properties.normalizedBatchSize());
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

  public int pollOnce() {
    try {
      return pollOnceBatch();
    } catch (Exception ex) {
      log.warn("outbox poll failed", ex);
      return 0;
    }
  }

  private void pollLoop() {
    while (running.get()) {
      int published = pollOnce();
      // 满批说明可能还有积压，立刻继续排空，不空等间隔
      if (published >= properties.normalizedBatchSize()) {
        wakeups.drainPermits();
        continue;
      }
      awaitInterval();
    }
  }

  /** 等待"提交后唤醒"信号，最多等一个轮询间隔（兜底），有信号则立即返回。 */
  private void awaitInterval() {
    try {
      wakeups.drainPermits();
      wakeups.tryAcquire(properties.normalizedInterval().toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      running.set(false);
    }
  }

  /**
   * 唤醒轮询线程立即发布（best-effort）。由 {@link OutboxWriter} 在事务提交后调用，
   * 使常态推送延迟≈0；即便漏掉唤醒，下一轮间隔轮询也会补发。
   */
  public void wakeup() {
    wakeups.release();
  }

  private int pollOnceBatch() {
    LocalDateTime now = now();
    List<OutboxEntity> events = outboxMapper.selectClaimCandidates(
        now,
        properties.normalizedMaxRetries(),
        properties.normalizedBatchSize());

    int published = 0;
    for (OutboxEntity event : events) {
      if (!claim(event, now)) {
        continue;
      }
      if (publishOne(event)) {
        published++;
      }
    }
    return published;
  }

  private boolean publishOne(OutboxEntity event) {
    try {
      publisher.publish(toRabbitMqEvent(event));
      int deleted = outboxMapper.deleteClaimed(
          event.getId(), claimOwner, OutboxWriter.STATUS_PROCESSING);
      if (deleted == 0) {
        log.warn("outbox event published but delete returned 0, event_id={}", event.getId());
      }
      return true;
    } catch (Exception ex) {
      markFailed(event, ex);
      return false;
    }
  }

  private RabbitMqEvent toRabbitMqEvent(OutboxEntity event) {
    return new RabbitMqEvent(
        event.getId(),
        event.getTenantId(),
        event.getEventType(),
        event.getRoutingKey(),
        event.getPayload());
  }

  private void markFailed(OutboxEntity event, Exception ex) {
    int nextRetryCount = safeRetryCount(event) + 1;
    int nextStatus = nextRetryCount >= properties.normalizedMaxRetries()
        ? OutboxWriter.STATUS_DEAD
        : OutboxWriter.STATUS_FAILED;
    int released = outboxMapper.releaseClaim(
        event.getId(),
        claimOwner,
        nextStatus,
        nextRetryCount,
        OutboxWriter.STATUS_PROCESSING);
    if (released == 0) {
      log.warn("outbox event publish failed but claim was lost, event_id={}", event.getId(), ex);
      return;
    }

    if (nextRetryCount >= properties.normalizedMaxRetries()) {
      log.error("outbox event reached max retries, event_id={}, retry_count={}",
          event.getId(), nextRetryCount, ex);
    } else {
      log.warn("outbox event publish failed, event_id={}, retry_count={}",
          event.getId(), nextRetryCount, ex);
    }
  }

  private int safeRetryCount(OutboxEntity event) {
    return event.getRetryCount() == null ? 0 : event.getRetryCount();
  }

  private boolean claim(OutboxEntity event, LocalDateTime now) {
    LocalDateTime claimUntil = now.plus(properties.normalizedClaimTtl());
    int claimed = outboxMapper.claim(
        event.getId(),
        claimOwner,
        claimUntil,
        now,
        properties.normalizedMaxRetries(),
        OutboxWriter.STATUS_PENDING,
        OutboxWriter.STATUS_FAILED,
        OutboxWriter.STATUS_PROCESSING);
    return claimed == 1;
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private static String defaultClaimOwner() {
    return ProcessHandle.current().pid() + "-" + UUID.randomUUID();
  }
}
