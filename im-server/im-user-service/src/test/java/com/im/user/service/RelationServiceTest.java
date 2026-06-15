package com.im.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.im.common.tenant.TenantContext;
import com.im.user.dao.mapper.FriendMapper;
import com.im.user.dao.mapper.TenantConfigMapper;
import com.im.user.dao.mapper.UserBlacklistMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class RelationServiceTest {

  @Mock
  private UserBlacklistMapper blacklistMapper;

  @Mock
  private FriendMapper friendMapper;

  @Mock
  private TenantConfigMapper tenantConfigMapper;

  private RelationService relationService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    relationService = new RelationService(blacklistMapper, friendMapper, tenantConfigMapper);
  }

  @Test
  void returnsBlockedWhenPeerBlacklistedSender() {
    when(blacklistMapper.selectCount(any())).thenReturn(1L);

    RelationService.RelationCheckResult result = checkWithTenant(100L, 200L);

    assertThat(result.blockedByPeer()).isTrue();
    assertThat(result.friendRequiredUnmet()).isFalse();
  }

  @Test
  void allowsOpenC2cWhenNotBlacklisted() {
    when(blacklistMapper.selectCount(any())).thenReturn(0L);

    RelationService.RelationCheckResult result = checkWithTenant(100L, 200L);

    assertThat(result.blockedByPeer()).isFalse();
    assertThat(result.friendRequiredUnmet()).isFalse();
  }

  @Test
  void requiresFriendWhenTenantSwitchOnAndNotFriend() {
    when(blacklistMapper.selectCount(any())).thenReturn(0L);
    when(tenantConfigMapper.selectFriendRequired()).thenReturn(1);
    when(friendMapper.selectCount(any())).thenReturn(0L);

    RelationService.RelationCheckResult result = checkWithTenant(100L, 200L);

    assertThat(result.friendRequiredUnmet()).isTrue();
  }

  @Test
  void allowsWhenTenantSwitchOnAndAlreadyFriend() {
    when(blacklistMapper.selectCount(any())).thenReturn(0L);
    when(tenantConfigMapper.selectFriendRequired()).thenReturn(1);
    when(friendMapper.selectCount(any())).thenReturn(1L);

    RelationService.RelationCheckResult result = checkWithTenant(100L, 200L);

    assertThat(result.friendRequiredUnmet()).isFalse();
  }

  private RelationService.RelationCheckResult checkWithTenant(long fromUserId, long toUserId) {
    AtomicReference<RelationService.RelationCheckResult> result = new AtomicReference<>();
    TenantContext.runWithTenant(1L, () -> result.set(relationService.check(fromUserId, toUserId)));
    return result.get();
  }
}
