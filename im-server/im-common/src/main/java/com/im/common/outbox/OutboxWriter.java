package com.im.common.outbox;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.outbox.dao.entity.OutboxEntity;
import com.im.common.outbox.dao.mapper.CommonOutboxMapper;
import com.im.common.tenant.TenantContext;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class OutboxWriter {

  public static final int STATUS_PENDING = 0;
  public static final int STATUS_FAILED = 1;
  public static final int STATUS_DEAD = 2;
  public static final int STATUS_PROCESSING = 3;

  private static final int MAX_EVENT_TYPE_LENGTH = 64;
  private static final int MAX_ROUTING_KEY_LENGTH = 128;
  // 列已放宽为 MEDIUMBLOB（V11/R-1），这里留足余量做防御上限（content 上限仅 8KB，正常远不及）。
  private static final int MAX_PAYLOAD_BYTES = 1_048_576;

  private final CommonOutboxMapper outboxMapper;
  private final Clock clock;
  private final OutboxPoller poller;

  @Autowired
  public OutboxWriter(CommonOutboxMapper outboxMapper, OutboxPoller poller) {
    this(outboxMapper, Clock.systemUTC(), poller);
  }

  OutboxWriter(CommonOutboxMapper outboxMapper, Clock clock) {
    this(outboxMapper, clock, null);
  }

  OutboxWriter(CommonOutboxMapper outboxMapper, Clock clock, OutboxPoller poller) {
    this.outboxMapper = outboxMapper;
    this.clock = clock;
    this.poller = poller;
  }

  public OutboxEntity write(String eventType, String routingKey, byte[] payload) {
    return write(TenantContext.requiredTenantId(), eventType, routingKey, payload);
  }

  public OutboxEntity write(long tenantId, String eventType, String routingKey, byte[] payload) {
    validate(tenantId, eventType, routingKey, payload);

    OutboxEntity entity = new OutboxEntity();
    entity.setTenantId(tenantId);
    entity.setEventType(eventType);
    entity.setRoutingKey(routingKey);
    entity.setPayload(payload);
    entity.setStatus(STATUS_PENDING);
    entity.setRetryCount(0);
    entity.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    outboxMapper.insert(entity);
    wakePollerAfterCommit();
    return entity;
  }

  /**
   * 事务提交后唤醒轮询线程立即发布（D-1），把常态推送延迟从"一个轮询间隔"降到≈0。
   * 必须等提交后再唤醒——否则 poller 的 SELECT 看不到未提交的行。
   * best-effort：唤醒/发布失败仍由间隔轮询兜底，绝不影响已提交事务。
   */
  private void wakePollerAfterCommit() {
    if (poller == null) {
      return;
    }
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          poller.wakeup();
        }
      });
    } else {
      // 无事务（autocommit）场景：行已落库，直接唤醒
      poller.wakeup();
    }
  }

  private void validate(long tenantId, String eventType, String routingKey, byte[] payload) {
    if (tenantId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "tenant_id is required");
    }
    validateText("event_type", eventType, MAX_EVENT_TYPE_LENGTH);
    validateText("routing_key", routingKey, MAX_ROUTING_KEY_LENGTH);
    if (payload == null || payload.length == 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "outbox payload is required");
    }
    if (payload.length > MAX_PAYLOAD_BYTES) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "outbox payload exceeds limit");
    }
  }

  private void validateText(String field, String value, int maxLength) {
    if (!StringUtils.hasText(value)) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, field + " is required");
    }
    if (value.length() > maxLength) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, field + " exceeds limit");
    }
  }
}
