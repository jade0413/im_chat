package com.im.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.user.dao.entity.FriendEntity;
import com.im.user.dao.entity.UserBlacklistEntity;
import com.im.user.dao.mapper.FriendMapper;
import com.im.user.dao.mapper.TenantConfigMapper;
import com.im.user.dao.mapper.UserBlacklistMapper;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class RelationService {

  private static final int FRIEND_REQUIRED_ON = 1;
  // friend_required 是准静态租户配置，却被每条 C2C 消息读库（C-1）。按租户做短 TTL 缓存。
  private static final long FRIEND_REQUIRED_TTL_MS = 30_000L;
  // C-1b：已建会话每条消息都重复查黑名单/好友。对 (from,to) 关系结果做短 TTL 缓存。
  // 代价：拉黑/好友变更最多 RELATION_TTL_MS 后才对发送侧生效（IM 可接受，远优于逐条查库）。
  private static final long RELATION_TTL_MS = 3_000L;
  private static final int RELATION_CACHE_MAX = 200_000;

  private final UserBlacklistMapper blacklistMapper;
  private final FriendMapper friendMapper;
  private final TenantConfigMapper tenantConfigMapper;
  // tenantId -> [friendRequired 值, 过期时刻 epochMs]；租户数有限，无需淘汰。
  private final ConcurrentMap<Long, long[]> friendRequiredCache = new ConcurrentHashMap<>();
  // "tenant:from:to" -> [blocked(0/1), friendUnmet(0/1), 过期时刻 epochMs]；超上限 clear 重建。
  private final ConcurrentMap<String, long[]> relationCache = new ConcurrentHashMap<>();

  public RelationService(UserBlacklistMapper blacklistMapper, FriendMapper friendMapper,
      TenantConfigMapper tenantConfigMapper) {
    this.blacklistMapper = blacklistMapper;
    this.friendMapper = friendMapper;
    this.tenantConfigMapper = tenantConfigMapper;
  }

  public RelationCheckResult check(long fromUserId, long toUserId) {
    long tenantId = TenantContext.requiredTenantId();
    if (fromUserId <= 0 || toUserId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "from_user_id and to_user_id must be positive");
    }
    // C-1b：命中 (from,to) 关系缓存即返回，省掉已建会话逐条黑名单/好友查库。
    long now = System.currentTimeMillis();
    String key = tenantId + ":" + fromUserId + ":" + toUserId;
    long[] cached = relationCache.get(key);
    if (cached != null && cached[2] > now) {
      return new RelationCheckResult(cached[0] == 1, cached[1] == 1);
    }

    boolean blockedByPeer = blacklistMapper.selectCount(Wrappers.lambdaQuery(UserBlacklistEntity.class)
        .eq(UserBlacklistEntity::getUserId, toUserId)
        .eq(UserBlacklistEntity::getBlockedUserId, fromUserId)) > 0;

    // D17 好友制：默认开启。仅租户显式关闭 friend_required=0 时允许开放式单聊。
    boolean friendRequiredUnmet = false;
    if (friendRequired(tenantId) == FRIEND_REQUIRED_ON) {
      boolean isFriend = friendMapper.selectCount(Wrappers.lambdaQuery(FriendEntity.class)
          .eq(FriendEntity::getUserId, fromUserId)
          .eq(FriendEntity::getFriendUserId, toUserId)) > 0;
      friendRequiredUnmet = !isFriend;
    }

    if (relationCache.size() >= RELATION_CACHE_MAX) {
      relationCache.clear();
    }
    relationCache.put(key, new long[] {blockedByPeer ? 1 : 0, friendRequiredUnmet ? 1 : 0,
        now + RELATION_TTL_MS});
    return new RelationCheckResult(blockedByPeer, friendRequiredUnmet);
  }

  /** friend_required 短 TTL 缓存（C-1）：默认开启；变更最多 30s 后生效，可接受。 */
  private int friendRequired(long tenantId) {
    long now = System.currentTimeMillis();
    long[] cached = friendRequiredCache.get(tenantId);
    if (cached != null && cached[1] > now) {
      return (int) cached[0];
    }
    Integer value = tenantConfigMapper.selectFriendRequired();
    int resolved = value == null ? FRIEND_REQUIRED_ON : value;
    friendRequiredCache.put(tenantId, new long[] {resolved, now + FRIEND_REQUIRED_TTL_MS});
    return resolved;
  }

  public record RelationCheckResult(boolean blockedByPeer, boolean friendRequiredUnmet) {
  }
}
