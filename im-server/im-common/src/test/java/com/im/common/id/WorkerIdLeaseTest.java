package com.im.common.id;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class WorkerIdLeaseTest {

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  /**
   * 另一个实例持有租约且剩余 TTL 超过 maxWait → 立即抛异常，不等待。
   */
  @Test
  void failsFastWhenWorkerIdIsAlreadyLeased() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(eq("im:worker:1"), anyString(), any(Duration.class)))
        .thenReturn(false);
    // 剩余 TTL = 25s > maxWait(5s) → 应立即 fail
    when(redisTemplate.getExpire(eq("im:worker:1"), eq(TimeUnit.MILLISECONDS)))
        .thenReturn(25_000L);

    WorkerIdLease lease = new WorkerIdLease(
        redisTemplate,
        1L,
        Duration.ofSeconds(12),
        Duration.ofSeconds(4),
        Duration.ofSeconds(5));   // maxWait=5s，25s > 5s → 不等待直接抛

    assertThatThrownBy(lease::afterPropertiesSet)
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INTERNAL_ERROR);
  }
}
