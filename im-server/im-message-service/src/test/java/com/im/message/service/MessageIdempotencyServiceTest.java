package com.im.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.message.dao.entity.MessageEntity;
import com.im.message.dao.mapper.MessageMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class MessageIdempotencyServiceTest {

  @Mock
  private MessageMapper messageMapper;

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @Test
  void findsExistingMessageByClientMsgId() {
    MessageEntity existing = new MessageEntity();
    existing.setId(1L);
    when(messageMapper.selectOne(anyWrapper())).thenReturn(existing);

    MessageEntity result = new MessageIdempotencyService(messageMapper, redisTemplate)
        .findExisting("client-1");

    assertThat(result).isSameAs(existing);
  }

  @Test
  void acquiresRedisDedupKey() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent("dedup:1:client-1", "1", Duration.ofSeconds(30)))
        .thenReturn(true);

    boolean acquired = new MessageIdempotencyService(messageMapper, redisTemplate)
        .tryAcquire(1L, "client-1");

    assertThat(acquired).isTrue();
  }

  @Test
  void rejectsNullRedisDedupResult() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent("dedup:1:client-1", "1", Duration.ofSeconds(30)))
        .thenReturn(null);

    assertThatThrownBy(() -> new MessageIdempotencyService(messageMapper, redisTemplate)
        .tryAcquire(1L, "client-1"))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INTERNAL_ERROR);
  }

  @SuppressWarnings("unchecked")
  private Wrapper<MessageEntity> anyWrapper() {
    return any(Wrapper.class);
  }
}
