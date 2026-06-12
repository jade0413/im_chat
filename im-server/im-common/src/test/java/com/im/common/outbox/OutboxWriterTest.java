package com.im.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.outbox.dao.entity.OutboxEntity;
import com.im.common.outbox.dao.mapper.CommonOutboxMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxWriterTest {

  @Mock
  private CommonOutboxMapper outboxMapper;

  @Captor
  private ArgumentCaptor<OutboxEntity> entityCaptor;

  @Test
  void writesPendingEventForTenant() {
    OutboxWriter writer = new OutboxWriter(
        outboxMapper,
        Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneOffset.UTC));

    OutboxEntity written = writer.write(1L, "msg.saved", "msg.saved.1", new byte[] {1, 2, 3});

    verify(outboxMapper).insert(entityCaptor.capture());
    assertThat(written).isSameAs(entityCaptor.getValue());
    assertThat(written.getTenantId()).isEqualTo(1L);
    assertThat(written.getEventType()).isEqualTo("msg.saved");
    assertThat(written.getRoutingKey()).isEqualTo("msg.saved.1");
    assertThat(written.getStatus()).isEqualTo(OutboxWriter.STATUS_PENDING);
    assertThat(written.getRetryCount()).isZero();
    assertThat(written.getCreatedAt()).isEqualTo(LocalDateTime.parse("2026-06-13T00:00:00"));
  }

  @Test
  void rejectsOversizedPayloadBeforeDatabaseWrite() {
    OutboxWriter writer = new OutboxWriter(outboxMapper);

    assertThatThrownBy(() -> writer.write(1L, "msg.saved", "msg.saved.1", new byte[16_385]))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }
}
