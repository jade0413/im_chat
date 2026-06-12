package com.im.common.outbox;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.im.common.mq.RabbitMqEvent;
import com.im.common.mq.RabbitMqPublisher;
import com.im.common.outbox.dao.entity.OutboxEntity;
import com.im.common.outbox.dao.mapper.CommonOutboxMapper;
import com.im.common.tenant.TenantContext;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

@Service
public class OutboxPoller implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

  private final CommonOutboxMapper outboxMapper;
  private final RabbitMqPublisher publisher;
  private final OutboxProperties properties;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private ExecutorService executor;

  public OutboxPoller(CommonOutboxMapper outboxMapper,
      RabbitMqPublisher publisher,
      OutboxProperties properties) {
    this.outboxMapper = outboxMapper;
    this.publisher = publisher;
    this.properties = properties;
  }

  @Override
  public void start() {
    if (!properties.isEnabled() || !running.compareAndSet(false, true)) {
      return;
    }
    executor = Executors.newVirtualThreadPerTaskExecutor();
    executor.submit(this::pollLoop);
    log.info("outbox poller started, tenant_id={}, batch_size={}",
        properties.getTenantId(), properties.normalizedBatchSize());
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
      return TenantContext.callWithTenant(properties.getTenantId(), this::pollOnceInTenant);
    } catch (Exception ex) {
      log.warn("outbox poll failed, tenant_id={}", properties.getTenantId(), ex);
      return 0;
    }
  }

  private void pollLoop() {
    while (running.get()) {
      pollOnce();
      sleepInterval();
    }
  }

  private void sleepInterval() {
    try {
      Thread.sleep(properties.normalizedInterval().toMillis());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      running.set(false);
    }
  }

  private int pollOnceInTenant() {
    List<OutboxEntity> events = outboxMapper.selectList(Wrappers.<OutboxEntity>query()
        .in("status", OutboxWriter.STATUS_PENDING, OutboxWriter.STATUS_FAILED)
        .lt("retry_count", properties.normalizedMaxRetries())
        .orderByAsc("created_at")
        .last("LIMIT " + properties.normalizedBatchSize()));

    int published = 0;
    for (OutboxEntity event : events) {
      if (publishOne(event)) {
        published++;
      }
    }
    return published;
  }

  private boolean publishOne(OutboxEntity event) {
    try {
      publisher.publish(toRabbitMqEvent(event));
      int deleted = outboxMapper.deleteById(event.getId());
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
    outboxMapper.update(null, Wrappers.<OutboxEntity>update()
        .eq("id", event.getId())
        .set("status", OutboxWriter.STATUS_FAILED)
        .set("retry_count", nextRetryCount));

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
}
