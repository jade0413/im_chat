package com.im.push.service;

import com.im.common.redis.RedisKeys;
import com.im.push.config.PushProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PushEventDeduplicator {

  private final StringRedisTemplate redisTemplate;
  private final PushProperties properties;

  public PushEventDeduplicator(StringRedisTemplate redisTemplate, PushProperties properties) {
    this.redisTemplate = redisTemplate;
    this.properties = properties;
  }

  public boolean tryMark(long tenantId, long eventId) {
    Boolean marked = redisTemplate.opsForValue()
        .setIfAbsent(RedisKeys.pushEventDedup(tenantId, eventId), "1", properties.eventDedupTtl());
    return Boolean.TRUE.equals(marked);
  }
}
