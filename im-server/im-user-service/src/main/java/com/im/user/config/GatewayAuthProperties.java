package com.im.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "im.gateway-auth")
public record GatewayAuthProperties(int heartbeatIntervalSec) {

  public GatewayAuthProperties {
    if (heartbeatIntervalSec <= 0) {
      heartbeatIntervalSec = 30;
    }
  }
}
