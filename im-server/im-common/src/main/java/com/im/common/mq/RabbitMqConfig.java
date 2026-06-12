package com.im.common.mq;

import com.im.common.outbox.OutboxProperties;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
public class RabbitMqConfig {

  public static final String EVENTS_EXCHANGE = "im.events";

  @Bean
  public TopicExchange imEventsExchange() {
    return ExchangeBuilder.topicExchange(EVENTS_EXCHANGE).durable(true).build();
  }
}
