package com.im.message.moderation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.message.dao.mapper.MessageFileMetaMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
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

  @Bean
  public Clock moderationClock() {
    return Clock.systemUTC();
  }

  @Bean
  public MediaModerationProvider mediaModerationProvider(
      MessageFileMetaMapper fileMetaMapper,
      ModerationProperties properties,
      ObjectMapper objectMapper) {
    List<MediaModerationProvider> providers = new ArrayList<>();
    providers.add(new FileStatusMediaModerationProvider(fileMetaMapper));
    if (properties.mediaHttp().isEnabled()) {
      providers.add(new HttpMediaModerationProvider(properties.mediaHttp(), objectMapper));
    }
    return new CompositeMediaModerationProvider(providers);
  }
}
