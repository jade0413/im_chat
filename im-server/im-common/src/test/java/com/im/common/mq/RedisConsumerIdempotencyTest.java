package com.im.common.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisConsumerIdempotencyTest {

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @Test
  void marksEventIdWithNamespaceAndTenant() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(
        "consumer:dedup:push:1:10",
        "1",
        Duration.ofHours(1))).thenReturn(true);

    boolean marked = new RedisConsumerIdempotency(redisTemplate)
        .tryMarkEvent("push", 1L, 10L, Duration.ofHours(1));

    assertThat(marked).isTrue();
  }

  @Test
  void rejectsInvalidEventIdWithoutRedisCall() {
    boolean marked = new RedisConsumerIdempotency(redisTemplate)
        .tryMarkEvent("push", 1L, 0L, Duration.ofHours(1));

    assertThat(marked).isFalse();
  }
}
