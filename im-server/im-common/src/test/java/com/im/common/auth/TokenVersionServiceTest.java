package com.im.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TokenVersionServiceTest {

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @Test
  void issuesNextVersionWithRedisIncrement() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment("token_ver:1:100:mobile")).thenReturn(2L);

    TokenVersionService service = new TokenVersionService(redisTemplate);

    assertThat(service.nextVersion(1L, 100L, "mobile")).isEqualTo(2L);
  }

  @Test
  void rejectsStaleTokenVersion() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("token_ver:1:100:mobile")).thenReturn("3");

    TokenVersionService service = new TokenVersionService(redisTemplate);

    assertThatThrownBy(() -> service.ensureCurrent(1L, 100L, "mobile", 2L))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.TOKEN_INVALID);
  }
}
