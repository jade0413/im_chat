package com.im.user.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "im.auth")
public record AuthProperties(Jwt jwt) {

  public AuthProperties {
    if (jwt == null) {
      jwt = new Jwt(null, null, null, null);
    }
  }

  public record Jwt(String secret, String issuer, Duration accessTtl, Duration refreshTtl) {

    public Jwt {
      if (issuer == null || issuer.isBlank()) {
        issuer = "im-server";
      }
      if (accessTtl == null) {
        accessTtl = Duration.ofHours(2);
      }
      if (refreshTtl == null) {
        refreshTtl = Duration.ofDays(30);
      }
    }
  }
}
