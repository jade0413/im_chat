package com.im.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.user.dao.entity.UserEntity;
import com.im.user.dao.mapper.UserMapper;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 唯一用户名（D42）：自填、租户内唯一、可分享的对外"加我"标识，独立于登录用 account。
 * 格式 {@code ^[a-z][a-z0-9_]{5,31}$}（字母开头，小写字母/数字/下划线，6–32 位）。
 * visitor 不分配；频率限制留 TODO（实现期定）。
 */
@Service
public class UsernameService {

  static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{5,31}$");
  private static final int USER_TYPE_VISITOR = 3;

  private final UserMapper userMapper;

  public UsernameService(UserMapper userMapper) {
    this.userMapper = userMapper;
  }

  /**
   * 设置/修改当前用户的 username。
   *
   * @throws ImException VALIDATION_FAILED 格式非法/被占用；NO_PERMISSION 访客不可设置
   */
  @Transactional
  public void setUsername(long userId, String rawUsername) {
    TenantContext.requiredTenantId();
    String username = rawUsername == null ? "" : rawUsername.trim();
    if (!USERNAME_PATTERN.matcher(username).matches()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED,
          "username 格式非法，要求字母开头、小写字母/数字/下划线、6-32 位");
    }

    UserEntity user = userMapper.selectById(userId);
    if (user == null) {
      throw new ImException(ErrorCode.TOKEN_INVALID, "user not found");
    }
    if (user.getUserType() != null && user.getUserType() == USER_TYPE_VISITOR) {
      throw new ImException(ErrorCode.NO_PERMISSION, "访客不可设置 username");
    }
    if (username.equals(user.getUsername())) {
      return; // 未变更，幂等
    }

    // 租户内唯一（tenant_id 由 MyBatis 拦截器自动注入），排除自身
    Long taken = userMapper.selectCount(Wrappers.lambdaQuery(UserEntity.class)
        .eq(UserEntity::getUsername, username)
        .ne(UserEntity::getId, userId));
    if (taken != null && taken > 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "username 已被占用");
    }

    // TODO(D42): 修改频率限制（如每年 N 次），实现期接入
    UserEntity update = new UserEntity();
    update.setId(userId);
    update.setUsername(username);
    userMapper.updateById(update);
  }
}
