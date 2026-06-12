package com.im.common.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.im.common.error.ImException;
import com.im.common.outbox.OutboxProperties;
import com.im.common.trace.TraceContext;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class RabbitMqPublisherTest {

  @Mock
  private RabbitTemplate rabbitTemplate;

  @Test
  void publishesBytesWithRequiredHeadersAndWaitsForAck() {
    AtomicReference<Message> processed = new AtomicReference<>();
    doAnswer(invocation -> {
      MessagePostProcessor processor = invocation.getArgument(3, MessagePostProcessor.class);
      CorrelationData correlationData = invocation.getArgument(4, CorrelationData.class);
      processed.set(processor.postProcessMessage(new Message(new byte[] {1}, new MessageProperties())));
      correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
      return null;
    }).when(rabbitTemplate).convertAndSend(
        anyString(),
        anyString(),
        any(Object.class),
        any(MessagePostProcessor.class),
        any(CorrelationData.class));

    RabbitMqPublisher publisher = new RabbitMqPublisher(rabbitTemplate, properties());
    TraceContext.runWithTraceId("trace-1",
        () -> publisher.publish(new RabbitMqEvent(10L, 1L, "msg.saved", "msg.saved.1", new byte[] {1})));

    MessageProperties headers = processed.get().getMessageProperties();
    assertThat(headers.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_BYTES);
    assertThat((Object) headers.getHeader(RabbitMqPublisher.HEADER_TENANT_ID)).isEqualTo(1L);
    assertThat((Object) headers.getHeader(RabbitMqPublisher.HEADER_TRACE_ID)).isEqualTo("trace-1");
    assertThat((Object) headers.getHeader(RabbitMqPublisher.HEADER_EVENT_ID)).isEqualTo(10L);
    assertThat((Object) headers.getHeader(RabbitMqPublisher.HEADER_EVENT_TYPE)).isEqualTo("msg.saved");
  }

  @Test
  void rejectsNackConfirm() {
    doAnswer(invocation -> {
      CorrelationData correlationData = invocation.getArgument(4, CorrelationData.class);
      correlationData.getFuture().complete(new CorrelationData.Confirm(false, "no route"));
      return null;
    }).when(rabbitTemplate).convertAndSend(
        anyString(),
        anyString(),
        any(Object.class),
        any(MessagePostProcessor.class),
        any(CorrelationData.class));

    RabbitMqPublisher publisher = new RabbitMqPublisher(rabbitTemplate, properties());

    assertThatThrownBy(() -> publisher.publish(
        new RabbitMqEvent(10L, 1L, "msg.saved", "msg.saved.1", new byte[] {1})))
        .isInstanceOf(ImException.class)
        .hasMessageContaining("rabbitmq publish nack");
  }

  private OutboxProperties properties() {
    OutboxProperties properties = new OutboxProperties();
    properties.setConfirmTimeout(Duration.ofSeconds(1));
    return properties;
  }
}
