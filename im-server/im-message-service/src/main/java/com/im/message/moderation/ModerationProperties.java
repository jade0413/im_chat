package com.im.message.moderation;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "im.moderation")
public record ModerationProperties(
    Boolean enabled,
    Duration eventDedupTtl,
    String msgSavedQueue,
    String wordReloadQueue,
    MediaHttp mediaHttp
) {

  public ModerationProperties {
    eventDedupTtl = normalize(eventDedupTtl, Duration.ofHours(24));
    msgSavedQueue = textOrDefault(msgSavedQueue, "im.moderation.msg.saved");
    wordReloadQueue = textOrDefault(wordReloadQueue, "im.moderation.word.reload");
    if (mediaHttp == null) {
      mediaHttp = new MediaHttp(null, null, null, null, null, null, null, null);
    }
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

  public record MediaHttp(
      Boolean enabled,
      String endpoint,
      String provider,
      String bearerToken,
      String hmacSecret,
      Duration timeout,
      Double revokeThreshold,
      Boolean failClosed
  ) {

    public MediaHttp {
      provider = textOrDefault(provider, ModerationConstants.PROVIDER_HTTP_MEDIA);
      timeout = normalize(timeout, Duration.ofSeconds(3));
      revokeThreshold = revokeThreshold == null ? 0.9D : revokeThreshold;
      failClosed = failClosed != null && failClosed;
    }

    public boolean isEnabled() {
      return enabled != null && enabled && endpoint != null && !endpoint.isBlank();
    }
  }
}
