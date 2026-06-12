package com.im.common.mq;

import static org.assertj.core.api.Assertions.assertThat;

import com.im.common.outbox.OutboxProperties;
import com.im.common.trace.TraceContext;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RabbitMqPublisherIntegrationTest {

  @Container
  private static final RabbitMQContainer RABBIT = new RabbitMQContainer(
      DockerImageName.parse("rabbitmq:3.13-management"))
      .withUser("im_test", "im_test_pwd");

  private CachingConnectionFactory connectionFactory;

  @AfterEach
  void tearDown() {
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
  }

  @Test
  void publishesToRabbitMqAndReceivesMessage() {
    connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
    connectionFactory.setUsername("im_test");
    connectionFactory.setPassword("im_test_pwd");
    connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);

    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    RabbitAdmin admin = new RabbitAdmin(connectionFactory);
    TopicExchange exchange = new RabbitMqConfig().imEventsExchange();
    Queue queue = QueueBuilder.durable("test.msg.saved." + UUID.randomUUID()).build();
    Binding binding = BindingBuilder.bind(queue).to(exchange).with("msg.saved.1");
    admin.declareExchange(exchange);
    admin.declareQueue(queue);
    admin.declareBinding(binding);

    OutboxProperties properties = new OutboxProperties();
    properties.setConfirmTimeout(Duration.ofSeconds(5));
    RabbitMqPublisher publisher = new RabbitMqPublisher(template, properties);

    TraceContext.runWithTraceId("trace-it",
        () -> publisher.publish(new RabbitMqEvent(10L, 1L, "msg.saved", "msg.saved.1", new byte[] {7})));

    Message received = template.receive(queue.getName(), 5_000);
    assertThat(received).isNotNull();
    assertThat(received.getBody()).containsExactly(7);
    assertThat((Object) received.getMessageProperties().getHeader(RabbitMqPublisher.HEADER_TENANT_ID))
        .isEqualTo(1L);
    assertThat((Object) received.getMessageProperties().getHeader(RabbitMqPublisher.HEADER_EVENT_ID))
        .isEqualTo(10L);
    assertThat((Object) received.getMessageProperties().getHeader(RabbitMqPublisher.HEADER_TRACE_ID))
        .isEqualTo("trace-it");
  }
}
