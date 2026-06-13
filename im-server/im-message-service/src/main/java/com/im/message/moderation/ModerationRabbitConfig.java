package com.im.message.moderation;

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
@EnableConfigurationProperties(ModerationProperties.class)
public class ModerationRabbitConfig {

  @Bean
  public Queue moderationMsgSavedQueue(ModerationProperties properties) {
    return new Queue(properties.msgSavedQueue(), true);
  }

  @Bean
  public Queue moderationWordReloadQueue(ModerationProperties properties) {
    return new Queue(properties.wordReloadQueue(), true);
  }

  @Bean
  public Binding moderationMsgSavedBinding(Queue moderationMsgSavedQueue,
      TopicExchange imEventsExchange) {
    return BindingBuilder.bind(moderationMsgSavedQueue)
        .to(imEventsExchange)
        .with("msg.saved.*");
  }

  @Bean
  public Binding moderationWordReloadBinding(Queue moderationWordReloadQueue,
      TopicExchange imEventsExchange) {
    return BindingBuilder.bind(moderationWordReloadQueue)
        .to(imEventsExchange)
        .with("word.reload");
  }
}
