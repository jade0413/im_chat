package com.im.push.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.device.PlatformClass;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.redis.RedisKeys;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisOnlineRouteRepository implements OnlineRouteRepository {

  private static final DefaultRedisScript<Long> DELETE_IF_CURRENT = new DefaultRedisScript<>("""
      if redis.call('get', KEYS[1]) == ARGV[1] then
        return redis.call('del', KEYS[1])
      end
      return 0
      """, Long.class);

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public RedisOnlineRouteRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public void save(OnlineRoute route, Duration ttl) {
    redisTemplate.opsForValue().set(key(route), encode(route), ttl);
  }

  @Override
  public Optional<OnlineRoute> find(long tenantId, long userId, int platform) {
    String key = RedisKeys.route(tenantId, userId, PlatformClass.fromPlatform(platform).key());
    String raw = redisTemplate.opsForValue().get(key);
    return raw == null || raw.isBlank() ? Optional.empty() : Optional.of(decode(raw));
  }

  @Override
  public List<OnlineRoute> findAll(long tenantId, long userId) {
    Set<String> keys = redisTemplate.keys(RedisKeys.userRoutePattern(tenantId, userId));
    if (keys == null || keys.isEmpty()) {
      return List.of();
    }
    List<String> values = redisTemplate.opsForValue().multiGet(keys);
    if (values == null) {
      return List.of();
    }
    return values.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(this::decode)
        .toList();
  }

  @Override
  public boolean deleteIfCurrent(OnlineRoute route) {
    Long deleted = redisTemplate.execute(DELETE_IF_CURRENT,
        Collections.singletonList(key(route)), encode(route));
    return Long.valueOf(1L).equals(deleted);
  }

  private String key(OnlineRoute route) {
    return RedisKeys.route(route.tenantId(), route.userId(), route.platformClass());
  }

  private String encode(OnlineRoute route) {
    try {
      return objectMapper.writeValueAsString(route);
    } catch (Exception ex) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "failed to encode online route", ex);
    }
  }

  private OnlineRoute decode(String raw) {
    try {
      return objectMapper.readValue(raw, OnlineRoute.class);
    } catch (Exception ex) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "failed to decode online route", ex);
    }
  }
}
