package com.im.push.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@EnableConfigurationProperties(PushProperties.class)
public class PushRabbitConfig {

  @Bean
  public Queue pushMsgSavedQueue(PushProperties properties) {
    return new Queue(properties.msgSavedQueue(), true);
  }

  @Bean
  public Queue pushMsgRevokedQueue(PushProperties properties) {
    return new Queue(properties.msgRevokedQueue(), true);
  }

  @Bean
  public Binding pushMsgSavedBinding(Queue pushMsgSavedQueue, TopicExchange imEventsExchange) {
    return BindingBuilder.bind(pushMsgSavedQueue)
        .to(imEventsExchange)
        .with("msg.saved.*");
  }

  @Bean
  public Binding pushMsgRevokedBinding(Queue pushMsgRevokedQueue, TopicExchange imEventsExchange) {
    return BindingBuilder.bind(pushMsgRevokedQueue)
        .to(imEventsExchange)
        .with("msg.revoked.*");
  }
}
