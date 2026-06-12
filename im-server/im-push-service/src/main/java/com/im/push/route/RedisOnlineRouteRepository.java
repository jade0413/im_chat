package com.im.push.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.device.PlatformClass;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.redis.RedisKeys;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
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
    validateRouteOwner(tenantId, userId);
    return findAllByUsers(tenantId, List.of(userId));
  }

  @Override
  public List<OnlineRoute> findAllByUsers(long tenantId, Collection<Long> userIds) {
    validateTenant(tenantId);
    if (userIds == null || userIds.isEmpty()) {
      return List.of();
    }
    List<String> keys = routeKeys(tenantId, userIds);
    if (keys.isEmpty()) {
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

  private List<String> routeKeys(long tenantId, Collection<Long> userIds) {
    LinkedHashSet<Long> normalizedUserIds = new LinkedHashSet<>();
    for (Long userId : userIds) {
      if (userId != null && userId > 0) {
        normalizedUserIds.add(userId);
      }
    }
    if (normalizedUserIds.isEmpty()) {
      return List.of();
    }
    List<String> keys = new ArrayList<>(normalizedUserIds.size() * PlatformClass.values().length);
    for (Long userId : normalizedUserIds) {
      for (PlatformClass platformClass : PlatformClass.values()) {
        keys.add(RedisKeys.route(tenantId, userId, platformClass.key()));
      }
    }
    return keys;
  }

  private void validateRouteOwner(long tenantId, long userId) {
    RedisKeys.route(tenantId, userId, PlatformClass.MOBILE.key());
  }

  private void validateTenant(long tenantId) {
    if (tenantId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "tenantId must be positive");
    }
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
