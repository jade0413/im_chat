package com.im.common.id;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import java.time.Duration;
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

  @Test
  void failsFastWhenWorkerIdIsAlreadyLeased() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(org.mockito.ArgumentMatchers.eq("im:worker:1"),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(30)))).thenReturn(false);

    WorkerIdLease lease = new WorkerIdLease(redisTemplate, 1L,
        Duration.ofSeconds(30), Duration.ofSeconds(10));

    assertThatThrownBy(lease::afterPropertiesSet)
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INTERNAL_ERROR);
  }
}
