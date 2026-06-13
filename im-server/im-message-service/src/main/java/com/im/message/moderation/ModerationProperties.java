package com.im.message.moderation;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "im.moderation")
public record ModerationProperties(
    Boolean enabled,
    Duration eventDedupTtl,
    String msgSavedQueue,
    String wordReloadQueue
) {

  public ModerationProperties {
    eventDedupTtl = normalize(eventDedupTtl, Duration.ofHours(24));
    msgSavedQueue = textOrDefault(msgSavedQueue, "im.moderation.msg.saved");
    wordReloadQueue = textOrDefault(wordReloadQueue, "im.moderation.word.reload");
  }

  public boolean isEnabled() {
    return enabled == null || enabled;
  }

  private static Duration normalize(Duration value, Duration fallback) {
    return value == null || value.isZero() || value.isNegative() ? fallback : value;
  }

  private static String textOrDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
