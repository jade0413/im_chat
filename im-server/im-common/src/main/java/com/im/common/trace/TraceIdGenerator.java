package com.im.common.trace;

import java.security.SecureRandom;
import java.time.Instant;

public final class TraceIdGenerator {

  private static final SecureRandom RANDOM = new SecureRandom();

  private TraceIdGenerator() {
  }

  public static String generate() {
    long now = Instant.now().toEpochMilli();
    long random = RANDOM.nextLong() & Long.MAX_VALUE;
    return "trc_" + Long.toString(now, 36) + "_" + Long.toString(random, 36);
  }
}
