package com.im.common.mq;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;

class RabbitMqConfigTest {

  @Test
  void declaresDurableEventsTopicExchange() {
    TopicExchange exchange = new RabbitMqConfig().imEventsExchange();

    assertThat(exchange.getName()).isEqualTo("im.events");
    assertThat(exchange.isDurable()).isTrue();
  }
}
