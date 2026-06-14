package com.im.cs.agent.service;

import com.im.common.device.PlatformClass;
import com.im.common.redis.RedisKeys;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CsVisitorPresenceService {

  private final StringRedisTemplate redisTemplate;

  public CsVisitorPresenceService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public boolean isOnline(long tenantId, long userId) {
    if (tenantId <= 0 || userId <= 0) {
      return false;
    }
    for (PlatformClass platformClass : PlatformClass.values()) {
      Boolean exists = redisTemplate.hasKey(RedisKeys.route(tenantId, userId, platformClass.key()));
      if (Boolean.TRUE.equals(exists)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 批量查询多个用户的在线状态（坐席工作台列表用）。
   * 用一次 MGET 取所有 route key，路由 key 存在即视为在线，消除逐用户 N 次往返。
   */
  public Map<Long, Boolean> onlineStatus(long tenantId, Collection<Long> userIds) {
    Map<Long, Boolean> result = new HashMap<>();
    if (tenantId <= 0 || userIds == null || userIds.isEmpty()) {
      return result;
    }
    List<Long> ids = userIds.stream().filter(id -> id != null && id > 0).distinct().toList();
    if (ids.isEmpty()) {
      return result;
    }
    PlatformClass[] platforms = PlatformClass.values();
    List<String> keys = new ArrayList<>(ids.size() * platforms.length);
    for (Long id : ids) {
      for (PlatformClass platformClass : platforms) {
        keys.add(RedisKeys.route(tenantId, id, platformClass.key()));
      }
    }
    List<String> values = redisTemplate.opsForValue().multiGet(keys);
    int idx = 0;
    for (Long id : ids) {
      boolean online = false;
      for (int p = 0; p < platforms.length; p++) {
        if (values != null && values.get(idx) != null) {
          online = true;
        }
        idx++;
      }
      result.put(id, online);
    }
    return result;
  }
}
