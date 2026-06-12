package com.im.common.auth;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.redis.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenVersionService {

  private final StringRedisTemplate redisTemplate;

  public TokenVersionService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public long nextVersion(long tenantId, long userId, String platformClass) {
    Long version = redisTemplate.opsForValue().increment(
        RedisKeys.tokenVersion(tenantId, userId, platformClass));
    if (version == null || version <= 0) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "failed to issue token version");
    }
    return version;
  }

  public long currentVersion(long tenantId, long userId, String platformClass) {
    String raw = redisTemplate.opsForValue().get(RedisKeys.tokenVersion(tenantId, userId, platformClass));
    if (raw == null || raw.isBlank()) {
      return 0L;
    }
    try {
      long version = Long.parseLong(raw);
      return Math.max(version, 0L);
    } catch (NumberFormatException ex) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "invalid token version in redis", ex);
    }
  }

  public void ensureCurrent(long tenantId, long userId, String platformClass, long tokenVersion) {
    long current = currentVersion(tenantId, userId, platformClass);
    if (current != tokenVersion) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
  }
}
