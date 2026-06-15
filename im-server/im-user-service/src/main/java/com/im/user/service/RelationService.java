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
import org.springframework.stereotype.Service;

@Service
public class RelationService {

  private static final int FRIEND_REQUIRED_ON = 1;

  private final UserBlacklistMapper blacklistMapper;
  private final FriendMapper friendMapper;
  private final TenantConfigMapper tenantConfigMapper;

  public RelationService(UserBlacklistMapper blacklistMapper, FriendMapper friendMapper,
      TenantConfigMapper tenantConfigMapper) {
    this.blacklistMapper = blacklistMapper;
    this.friendMapper = friendMapper;
    this.tenantConfigMapper = tenantConfigMapper;
  }

  public RelationCheckResult check(long fromUserId, long toUserId) {
    TenantContext.requiredTenantId();
    if (fromUserId <= 0 || toUserId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "from_user_id and to_user_id must be positive");
    }
    boolean blockedByPeer = blacklistMapper.selectCount(Wrappers.lambdaQuery(UserBlacklistEntity.class)
        .eq(UserBlacklistEntity::getUserId, toUserId)
        .eq(UserBlacklistEntity::getBlockedUserId, fromUserId)) > 0;

    // D17 好友制：仅当租户开启 friend_required 时，非好友不可发消息（默认关闭→恒放行）
    boolean friendRequiredUnmet = false;
    Integer friendRequired = tenantConfigMapper.selectFriendRequired();
    if (friendRequired != null && friendRequired == FRIEND_REQUIRED_ON) {
      boolean isFriend = friendMapper.selectCount(Wrappers.lambdaQuery(FriendEntity.class)
          .eq(FriendEntity::getUserId, fromUserId)
          .eq(FriendEntity::getFriendUserId, toUserId)) > 0;
      friendRequiredUnmet = !isFriend;
    }
    return new RelationCheckResult(blockedByPeer, friendRequiredUnmet);
  }

  public record RelationCheckResult(boolean blockedByPeer, boolean friendRequiredUnmet) {
  }
}
