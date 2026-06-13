package com.im.common.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "im.outbox")
public class OutboxProperties {

  private boolean enabled = false;
  private int batchSize = 100;
  private Duration interval = Duration.ofMillis(100);
  private int maxRetries = 16;
  private Duration confirmTimeout = Duration.ofSeconds(5);
  private Duration claimTtl = Duration.ofSeconds(30);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public Duration getInterval() {
    return interval;
  }

  public void setInterval(Duration interval) {
    this.interval = interval;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  public Duration getConfirmTimeout() {
    return confirmTimeout;
  }

  public void setConfirmTimeout(Duration confirmTimeout) {
    this.confirmTimeout = confirmTimeout;
  }

  public Duration getClaimTtl() {
    return claimTtl;
  }

  public void setClaimTtl(Duration claimTtl) {
    this.claimTtl = claimTtl;
  }

  public int normalizedBatchSize() {
    return Math.max(1, batchSize);
  }

  public int normalizedMaxRetries() {
    return Math.max(1, maxRetries);
  }

  public Duration normalizedInterval() {
    return interval == null || interval.isNegative() || interval.isZero()
        ? Duration.ofMillis(100)
        : interval;
  }

  public Duration normalizedConfirmTimeout() {
    return confirmTimeout == null || confirmTimeout.isNegative() || confirmTimeout.isZero()
        ? Duration.ofSeconds(5)
        : confirmTimeout;
  }

  public Duration normalizedClaimTtl() {
    return claimTtl == null || claimTtl.isNegative() || claimTtl.isZero()
        ? Duration.ofSeconds(30)
        : claimTtl;
  }
}
