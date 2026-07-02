package com.im.call.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通话模块配置（D45）。
 *
 * <p>TURN 凭证走 coturn REST 机制（use-auth-secret）：turnSecret 必须与 coturn 的
 * static-auth-secret 一致；为空时只下发 STUN（P2P 打不通即失败，仅限内网联调）。
 */
@ConfigurationProperties(prefix = "im.call")
public record CallProperties(
    Duration ringTimeout,
    Duration activeTtl,
    List<String> stunUrls,
    List<String> turnUrls,
    String turnSecret,
    Duration turnCredentialTtl
) {

  public CallProperties {
    ringTimeout = normalize(ringTimeout, Duration.ofSeconds(60));
    activeTtl = normalize(activeTtl, Duration.ofHours(4));
    stunUrls = listOrDefault(stunUrls, List.of("stun:stun.l.google.com:19302"));
    turnUrls = listOrDefault(turnUrls, List.of());
    turnSecret = turnSecret == null ? "" : turnSecret;
    turnCredentialTtl = normalize(turnCredentialTtl, Duration.ofHours(1));
  }

  private static Duration normalize(Duration value, Duration fallback) {
    return value == null || value.isZero() || value.isNegative() ? fallback : value;
  }

  private static List<String> listOrDefault(List<String> value, List<String> fallback) {
    return value == null || value.isEmpty() ? fallback : List.copyOf(value);
  }
}
