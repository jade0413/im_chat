package com.im.message.moderation;

import com.im.common.redis.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ModerationEventDeduplicator {

  private final StringRedisTemplate redisTemplate;
  private final ModerationProperties properties;

  public ModerationEventDeduplicator(StringRedisTemplate redisTemplate,
      ModerationProperties properties) {
    this.redisTemplate = redisTemplate;
    this.properties = properties;
  }

  public boolean isMarked(long tenantId, long eventId) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.moderationEventDedup(tenantId, eventId)));
  }

  public void mark(long tenantId, long eventId) {
    redisTemplate.opsForValue()
        .set(RedisKeys.moderationEventDedup(tenantId, eventId), "1", properties.eventDedupTtl());
  }
}
