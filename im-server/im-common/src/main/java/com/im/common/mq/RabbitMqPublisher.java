package com.im.common.mq;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.outbox.OutboxProperties;
import com.im.common.trace.TraceContext;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class RabbitMqPublisher {

  public static final String HEADER_TENANT_ID = "tenant_id";
  public static final String HEADER_TRACE_ID = "trace_id";
  public static final String HEADER_EVENT_ID = "event_id";
  public static final String HEADER_EVENT_TYPE = "event_type";

  private final RabbitTemplate rabbitTemplate;
  private final OutboxProperties properties;

  public RabbitMqPublisher(RabbitTemplate rabbitTemplate, OutboxProperties properties) {
    this.rabbitTemplate = rabbitTemplate;
    this.properties = properties;
  }

  public void publish(RabbitMqEvent event) {
    CorrelationData correlationData = new CorrelationData(Long.toString(event.eventId()));
    try {
      rabbitTemplate.convertAndSend(
          RabbitMqConfig.EVENTS_EXCHANGE,
          event.routingKey(),
          event.payload(),
          messagePostProcessor(event),
          correlationData);
      awaitConfirm(event, correlationData);
    } catch (AmqpException ex) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "rabbitmq publish failed", ex);
    }
  }

  private MessagePostProcessor messagePostProcessor(RabbitMqEvent event) {
    return message -> {
      MessageProperties messageProperties = message.getMessageProperties();
      messageProperties.setContentType(MessageProperties.CONTENT_TYPE_BYTES);
      messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
      messageProperties.setHeader(HEADER_TENANT_ID, event.tenantId());
      messageProperties.setHeader(HEADER_TRACE_ID, TraceContext.currentOrCreateTraceId());
      messageProperties.setHeader(HEADER_EVENT_ID, event.eventId());
      messageProperties.setHeader(HEADER_EVENT_TYPE, event.eventType());
      return message;
    };
  }

  private void awaitConfirm(RabbitMqEvent event, CorrelationData correlationData) {
    try {
      CorrelationData.Confirm confirm = correlationData.getFuture().get(
          properties.normalizedConfirmTimeout().toMillis(),
          TimeUnit.MILLISECONDS);
      if (!confirm.isAck()) {
        throw new ImException(ErrorCode.INTERNAL_ERROR,
            "rabbitmq publish nack: event_id=" + event.eventId() + ", reason=" + confirm.getReason());
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ImException(ErrorCode.INTERNAL_ERROR, "rabbitmq publish interrupted", ex);
    } catch (ExecutionException ex) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "rabbitmq publish confirm failed", ex);
    } catch (TimeoutException ex) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "rabbitmq publish confirm timeout", ex);
    }
  }
}
