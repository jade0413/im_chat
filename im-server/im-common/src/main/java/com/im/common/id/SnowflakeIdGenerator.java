package com.im.common.id;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SnowflakeIdGenerator {

  private static final long EPOCH_MILLIS = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
  private static final long WORKER_ID_BITS = 10L;
  private static final long SEQUENCE_BITS = 12L;
  private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
  private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
  private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
  private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

  private final long workerId;
  private final AtomicLong lastTimestamp = new AtomicLong(-1L);
  private final AtomicLong sequence = new AtomicLong(0L);

  public SnowflakeIdGenerator(@Value("${im.id.worker-id:1}") long workerId) {
    if (workerId < 0 || workerId > MAX_WORKER_ID) {
      throw new IllegalArgumentException("im.id.worker-id must be between 0 and " + MAX_WORKER_ID);
    }
    this.workerId = workerId;
  }

  public synchronized long nextId() {
    long timestamp = currentTimestamp();
    long last = lastTimestamp.get();
    if (timestamp < last) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "id generator clock moved backwards");
    }
    if (timestamp == last) {
      long nextSequence = (sequence.incrementAndGet()) & SEQUENCE_MASK;
      if (nextSequence == 0) {
        timestamp = waitNextMillis(last);
      }
      sequence.set(nextSequence);
    } else {
      sequence.set(0L);
    }
    lastTimestamp.set(timestamp);
    return ((timestamp - EPOCH_MILLIS) << TIMESTAMP_SHIFT)
        | (workerId << WORKER_ID_SHIFT)
        | sequence.get();
  }

  private long waitNextMillis(long lastTimestampValue) {
    long timestamp = currentTimestamp();
    while (timestamp <= lastTimestampValue) {
      timestamp = currentTimestamp();
    }
    return timestamp;
  }

  private long currentTimestamp() {
    return System.currentTimeMillis();
  }
}
