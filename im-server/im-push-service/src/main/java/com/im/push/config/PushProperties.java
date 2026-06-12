package com.im.push.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "im.push")
public record PushProperties(
    Duration routeTtl,
    Duration eventDedupTtl,
    String gatewayQueuePrefix,
    String msgSavedQueue
) {

  public PushProperties {
    routeTtl = normalize(routeTtl, Duration.ofSeconds(90));
    eventDedupTtl = normalize(eventDedupTtl, Duration.ofHours(24));
    gatewayQueuePrefix = textOrDefault(gatewayQueuePrefix, "push.gw.");
    msgSavedQueue = textOrDefault(msgSavedQueue, "im.push.msg.saved");
  }

  private static Duration normalize(Duration value, Duration fallback) {
    return value == null || value.isZero() || value.isNegative() ? fallback : value;
  }

  private static String textOrDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
