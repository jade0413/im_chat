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
import org.springframework.util.StringUtils;

@Service
public class OutboxWriter {

  public static final int STATUS_PENDING = 0;
  public static final int STATUS_FAILED = 1;
  public static final int STATUS_DEAD = 2;
  public static final int STATUS_PROCESSING = 3;

  private static final int MAX_EVENT_TYPE_LENGTH = 64;
  private static final int MAX_ROUTING_KEY_LENGTH = 128;
  private static final int MAX_PAYLOAD_BYTES = 16_384;

  private final CommonOutboxMapper outboxMapper;
  private final Clock clock;

  @Autowired
  public OutboxWriter(CommonOutboxMapper outboxMapper) {
    this(outboxMapper, Clock.systemUTC());
  }

  OutboxWriter(CommonOutboxMapper outboxMapper, Clock clock) {
    this.outboxMapper = outboxMapper;
    this.clock = clock;
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
    return entity;
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
