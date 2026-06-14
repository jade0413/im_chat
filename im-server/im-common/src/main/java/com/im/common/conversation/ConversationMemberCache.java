package com.im.common.conversation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.redis.RedisKeys;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 会话成员列表缓存（共享基础设施，放在 im-common 以便 conversation/group/push 等模块共用）。
 *
 * <p>用途：消息扇出（{@code getMembersResult}）每条消息都要取一次会话成员，群聊下成员查询是热点。
 * 这里只缓存「成员 userId 列表」——它变化很少（仅建群/加人/退群/踢人时变）；
 * 会话的 type/cs_status/agent_id 等会频繁变动的字段不进缓存，仍由调用方实时读会话行，
 * 保证 CS 路由（open/assigned/resolved、绑定坐席）始终新鲜。
 *
 * <p>失效策略：成员变更处显式 {@link #evict}；同时设置较短 TTL 作为兜底，
 * 即便漏掉一次 evict，过期后也会自动回源。
 */
@Component
public class ConversationMemberCache {

  private static final Logger log = LoggerFactory.getLogger(ConversationMemberCache.class);
  private static final Duration TTL = Duration.ofSeconds(60);
  private static final TypeReference<List<Long>> LIST_TYPE = new TypeReference<>() {
  };

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public ConversationMemberCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  public Optional<List<Long>> get(long tenantId, long convId) {
    try {
      String raw = redisTemplate.opsForValue().get(RedisKeys.convMembers(tenantId, convId));
      if (raw == null || raw.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(objectMapper.readValue(raw, LIST_TYPE));
    } catch (Exception ex) {
      // 缓存读取失败不应影响主流程，回源即可
      log.warn("conv member cache read failed, tenant_id={}, conv_id={}", tenantId, convId, ex);
      return Optional.empty();
    }
  }

  public void put(long tenantId, long convId, List<Long> userIds) {
    try {
      redisTemplate.opsForValue().set(
          RedisKeys.convMembers(tenantId, convId),
          objectMapper.writeValueAsString(userIds),
          TTL);
    } catch (Exception ex) {
      log.warn("conv member cache write failed, tenant_id={}, conv_id={}", tenantId, convId, ex);
    }
  }

  public void evict(long tenantId, long convId) {
    try {
      redisTemplate.delete(RedisKeys.convMembers(tenantId, convId));
    } catch (Exception ex) {
      log.warn("conv member cache evict failed, tenant_id={}, conv_id={}", tenantId, convId, ex);
    }
  }
}
