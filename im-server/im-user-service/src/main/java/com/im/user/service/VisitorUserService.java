package com.im.user.service;

import com.im.common.id.SnowflakeIdGenerator;
import com.im.user.dao.entity.UserEntity;
import com.im.user.dao.mapper.UserMapper;
import com.im.user.dto.TokenResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 访客用户创建 + JWT 签发（T31）。
 * 访客账号规则：account = "visitor_" + userId（满足 NOT NULL + 唯一约束），password_hash = null。
 */
@Service
public class VisitorUserService {

  /** user_type = 3（visitor），与 proto UserType.VISITOR 对齐 */
  private static final int USER_TYPE_VISITOR = 3;
  private static final int VERIFIED_TYPE_NONE = 0;
  private static final int STATUS_NORMAL = 1;
  private static final String TOKEN_TYPE = "Bearer";

  private final UserMapper userMapper;
  private final JwtService jwtService;
  private final SnowflakeIdGenerator idGenerator;

  public VisitorUserService(UserMapper userMapper, JwtService jwtService,
      SnowflakeIdGenerator idGenerator) {
    this.userMapper = userMapper;
    this.jwtService = jwtService;
    this.idGenerator = idGenerator;
  }

  /**
   * 创建访客用户，返回 userId 和 displayName。
   * 调用方（im-cs-service）已确认此 visitorToken 尚无对应 visitor_profile，需新建。
   *
   * @param tenantId    租户 ID（TenantContext 中已设置，DB 拦截器会自动注入）
   * @param displayName "访客XXXX" 格式，由 cs-service 生成
   * @return 新建用户的 userId
   */
  @Transactional
  public long createVisitorUser(long tenantId, String displayName) {
    long userId = idGenerator.nextId();

    UserEntity user = new UserEntity();
    user.setId(userId);
    user.setTenantId(tenantId);
    // 用 "visitor_" + userId 作为 account，满足 NOT NULL + 唯一约束
    user.setAccount("visitor_" + userId);
    user.setPasswordHash(null);    // 访客无密码
    user.setNickname(displayName); // "访客XXXX"
    user.setAvatar("");
    user.setUserType(USER_TYPE_VISITOR);
    user.setVerifiedType(VERIFIED_TYPE_NONE);
    user.setStatus(STATUS_NORMAL);
    // is_agent / agent_status 使用数据库 DEFAULT（0），此处不赋值

    userMapper.insert(user);
    return userId;
  }

  /**
   * 为已存在的访客用户签发 JWT（无 platform_class，访客为单连接场景）。
   */
  public TokenResponse issueVisitorToken(long tenantId, long userId) {
    // 访客不走多端互踢，使用无 platform_class 的简单 token
    String accessToken  = jwtService.createAccessToken(tenantId, userId);
    String refreshToken = jwtService.createRefreshToken(tenantId, userId);
    return new TokenResponse(
        accessToken,
        refreshToken,
        TOKEN_TYPE,
        jwtService.accessExpiresInSeconds(),
        jwtService.refreshExpiresInSeconds());
  }
}
