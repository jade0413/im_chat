package com.im.message.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.redis.RedisKeys;
import com.im.message.dao.entity.MessageEntity;
import com.im.message.dao.mapper.MessageMapper;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class MessageIdempotencyService {

  private static final Duration DEDUP_TTL = Duration.ofSeconds(30);
  private static final int WAIT_ATTEMPTS = 20;
  private static final Duration WAIT_INTERVAL = Duration.ofMillis(50);

  private final MessageMapper messageMapper;
  private final StringRedisTemplate redisTemplate;

  public MessageIdempotencyService(MessageMapper messageMapper, StringRedisTemplate redisTemplate) {
    this.messageMapper = messageMapper;
    this.redisTemplate = redisTemplate;
  }

  public MessageEntity findExisting(String clientMsgId) {
    return messageMapper.selectOne(Wrappers.lambdaQuery(MessageEntity.class)
        .eq(MessageEntity::getClientMsgId, clientMsgId));
  }

  public boolean tryAcquire(long tenantId, String clientMsgId) {
    Boolean acquired = redisTemplate.opsForValue()
        .setIfAbsent(RedisKeys.messageDedup(tenantId, clientMsgId), "1", DEDUP_TTL);
    if (acquired == null) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "redis dedup result is null");
    }
    return acquired;
  }

  public MessageEntity waitForExisting(String clientMsgId) {
    for (int i = 0; i < WAIT_ATTEMPTS; i++) {
      MessageEntity existing = findExisting(clientMsgId);
      if (existing != null) {
        return existing;
      }
      sleep();
    }
    throw new ImException(ErrorCode.INTERNAL_ERROR, "duplicate message is still processing");
  }

  private void sleep() {
    try {
      Thread.sleep(WAIT_INTERVAL.toMillis());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ImException(ErrorCode.INTERNAL_ERROR, "interrupted while waiting duplicate message", ex);
    }
  }
}
