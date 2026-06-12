package com.im.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.user.dao.entity.UserBlacklistEntity;
import com.im.user.dao.mapper.UserBlacklistMapper;
import org.springframework.stereotype.Service;

@Service
public class RelationService {

  private final UserBlacklistMapper blacklistMapper;

  public RelationService(UserBlacklistMapper blacklistMapper) {
    this.blacklistMapper = blacklistMapper;
  }

  public RelationCheckResult check(long fromUserId, long toUserId) {
    TenantContext.requiredTenantId();
    if (fromUserId <= 0 || toUserId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "from_user_id and to_user_id must be positive");
    }
    boolean blockedByPeer = blacklistMapper.selectCount(Wrappers.lambdaQuery(UserBlacklistEntity.class)
        .eq(UserBlacklistEntity::getUserId, toUserId)
        .eq(UserBlacklistEntity::getBlockedUserId, fromUserId)) > 0;
    return new RelationCheckResult(blockedByPeer, false);
  }

  public record RelationCheckResult(boolean blockedByPeer, boolean friendRequiredUnmet) {
  }
}
