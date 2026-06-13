package com.im.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.mq.RabbitMqEvent;
import com.im.common.mq.RabbitMqPublisher;
import com.im.common.outbox.dao.entity.OutboxEntity;
import com.im.common.outbox.dao.mapper.CommonOutboxMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

  @Mock
  private CommonOutboxMapper outboxMapper;

  @Mock
  private RabbitMqPublisher publisher;

  @Captor
  private ArgumentCaptor<RabbitMqEvent> eventCaptor;

  @Test
  void publishesAndDeletesConfirmedEvents() {
    OutboxProperties properties = new OutboxProperties();
    OutboxEntity event = event(10L, 1L, 0);
    when(outboxMapper.selectClaimCandidates(any(LocalDateTime.class), eq(16), eq(100)))
        .thenReturn(List.of(event));
    when(outboxMapper.claim(eq(10L), eq("test-owner"), any(LocalDateTime.class),
        any(LocalDateTime.class), eq(16), eq(OutboxWriter.STATUS_PENDING),
        eq(OutboxWriter.STATUS_FAILED), eq(OutboxWriter.STATUS_PROCESSING))).thenReturn(1);
    when(outboxMapper.deleteClaimed(10L, "test-owner", OutboxWriter.STATUS_PROCESSING)).thenReturn(1);

    int published = poller(properties).pollOnce();

    assertThat(published).isEqualTo(1);
    verify(publisher).publish(eventCaptor.capture());
    assertThat(eventCaptor.getValue().eventId()).isEqualTo(10L);
    assertThat(eventCaptor.getValue().tenantId()).isEqualTo(1L);
    assertThat(eventCaptor.getValue().routingKey()).isEqualTo("msg.saved.1");
    verify(outboxMapper).deleteClaimed(10L, "test-owner", OutboxWriter.STATUS_PROCESSING);
  }

  @Test
  void marksFailedEventsForRetryWithoutDeleting() {
    OutboxProperties properties = new OutboxProperties();
    OutboxEntity event = event(10L, 1L, 2);
    when(outboxMapper.selectClaimCandidates(any(LocalDateTime.class), eq(16), eq(100)))
        .thenReturn(List.of(event));
    when(outboxMapper.claim(eq(10L), eq("test-owner"), any(LocalDateTime.class),
        any(LocalDateTime.class), eq(16), eq(OutboxWriter.STATUS_PENDING),
        eq(OutboxWriter.STATUS_FAILED), eq(OutboxWriter.STATUS_PROCESSING))).thenReturn(1);
    doThrow(new ImException(ErrorCode.INTERNAL_ERROR, "publish failed"))
        .when(publisher).publish(any(RabbitMqEvent.class));
    when(outboxMapper.releaseClaim(10L, "test-owner", OutboxWriter.STATUS_FAILED, 3,
        OutboxWriter.STATUS_PROCESSING)).thenReturn(1);

    int published = poller(properties).pollOnce();

    assertThat(published).isZero();
    verify(outboxMapper).releaseClaim(
        10L, "test-owner", OutboxWriter.STATUS_FAILED, 3, OutboxWriter.STATUS_PROCESSING);
  }

  @Test
  void marksEventsDeadAfterMaxRetries() {
    OutboxProperties properties = new OutboxProperties();
    properties.setMaxRetries(3);
    OutboxEntity event = event(10L, 1L, 2);
    when(outboxMapper.selectClaimCandidates(any(LocalDateTime.class), eq(3), eq(100)))
        .thenReturn(List.of(event));
    when(outboxMapper.claim(eq(10L), eq("test-owner"), any(LocalDateTime.class),
        any(LocalDateTime.class), eq(3), eq(OutboxWriter.STATUS_PENDING),
        eq(OutboxWriter.STATUS_FAILED), eq(OutboxWriter.STATUS_PROCESSING))).thenReturn(1);
    doThrow(new ImException(ErrorCode.INTERNAL_ERROR, "publish failed"))
        .when(publisher).publish(any(RabbitMqEvent.class));
    when(outboxMapper.releaseClaim(10L, "test-owner", OutboxWriter.STATUS_DEAD, 3,
        OutboxWriter.STATUS_PROCESSING)).thenReturn(1);

    int published = poller(properties).pollOnce();

    assertThat(published).isZero();
    verify(outboxMapper).releaseClaim(
        10L, "test-owner", OutboxWriter.STATUS_DEAD, 3, OutboxWriter.STATUS_PROCESSING);
  }

  @Test
  void skipsEventWhenClaimFails() {
    OutboxProperties properties = new OutboxProperties();
    OutboxEntity event = event(10L, 1L, 0);
    when(outboxMapper.selectClaimCandidates(any(LocalDateTime.class), eq(16), eq(100)))
        .thenReturn(List.of(event));
    when(outboxMapper.claim(eq(10L), eq("test-owner"), any(LocalDateTime.class),
        any(LocalDateTime.class), eq(16), eq(OutboxWriter.STATUS_PENDING),
        eq(OutboxWriter.STATUS_FAILED), eq(OutboxWriter.STATUS_PROCESSING))).thenReturn(0);

    int published = poller(properties).pollOnce();

    assertThat(published).isZero();
    verify(publisher, never()).publish(any(RabbitMqEvent.class));
  }

  private OutboxEntity event(long id, long tenantId, int retryCount) {
    OutboxEntity event = new OutboxEntity();
    event.setId(id);
    event.setTenantId(tenantId);
    event.setEventType("msg.saved");
    event.setRoutingKey("msg.saved.1");
    event.setPayload(new byte[] {1, 2, 3});
    event.setStatus(OutboxWriter.STATUS_PENDING);
    event.setRetryCount(retryCount);
    event.setCreatedAt(LocalDateTime.parse("2026-06-13T00:00:00"));
    return event;
  }

  private OutboxPoller poller(OutboxProperties properties) {
    return new OutboxPoller(
        outboxMapper,
        publisher,
        properties,
        Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneOffset.UTC),
        "test-owner");
  }
}
