package com.im.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.id.SnowflakeIdGenerator;
import com.im.common.tenant.TenantContext;
import com.im.user.dao.entity.UserEntity;
import com.im.user.dao.mapper.UserMapper;
import com.im.user.dto.LoginRequest;
import com.im.user.dto.RegisterRequest;
import com.im.user.dto.TokenResponse;
import com.im.user.dto.UserProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final int USER_TYPE_MEMBER = 1;
  private static final int VERIFIED_TYPE_NONE = 0;
  private static final int STATUS_NORMAL = 1;
  private static final int STATUS_BANNED = 3;
  private static final String TOKEN_TYPE = "Bearer";

  private final UserMapper userMapper;
  private final PasswordService passwordService;
  private final JwtService jwtService;
  private final SnowflakeIdGenerator idGenerator;

  public AuthService(UserMapper userMapper, PasswordService passwordService, JwtService jwtService,
      SnowflakeIdGenerator idGenerator) {
    this.userMapper = userMapper;
    this.passwordService = passwordService;
    this.jwtService = jwtService;
    this.idGenerator = idGenerator;
  }

  @Transactional
  public TokenResponse register(RegisterRequest request) {
    long tenantId = TenantContext.requiredTenantId();
    String account = normalizeAccount(request.account());
    UserEntity existing = findByAccount(account);
    if (existing != null) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "account already exists");
    }

    UserEntity user = new UserEntity();
    user.setId(idGenerator.nextId());
    user.setTenantId(tenantId);
    user.setAccount(account);
    user.setPasswordHash(passwordService.hash(request.password()));
    user.setNickname(normalizeNickname(request.nickname(), account));
    user.setAvatar("");
    user.setUserType(USER_TYPE_MEMBER);
    user.setVerifiedType(VERIFIED_TYPE_NONE);
    user.setStatus(STATUS_NORMAL);
    userMapper.insert(user);

    return issueTokens(tenantId, user.getId());
  }

  public TokenResponse login(LoginRequest request) {
    long tenantId = TenantContext.requiredTenantId();
    UserEntity user = findByAccount(normalizeAccount(request.account()));
    if (user == null || !passwordService.matches(request.password(), user.getPasswordHash())) {
      throw new ImException(ErrorCode.TOKEN_INVALID, "account or password is incorrect");
    }
    ensureUserCanLogin(user);
    return issueTokens(tenantId, user.getId());
  }

  public TokenResponse refresh(String refreshToken) {
    long tenantId = TenantContext.requiredTenantId();
    TokenClaims claims = jwtService.verifyRefreshToken(refreshToken);
    if (claims.tenantId() != tenantId) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    UserEntity user = loadCurrentUser(claims.userId());
    ensureUserCanLogin(user);
    return issueTokens(tenantId, user.getId());
  }

  public UserProfileResponse currentUser(String accessToken) {
    long tenantId = TenantContext.requiredTenantId();
    TokenClaims claims = jwtService.verifyAccessToken(accessToken);
    if (claims.tenantId() != tenantId) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    return toProfile(loadCurrentUser(claims.userId()));
  }

  private UserEntity findByAccount(String account) {
    return userMapper.selectOne(Wrappers
        .lambdaQuery(UserEntity.class)
        .eq(UserEntity::getAccount, account));
  }

  private UserEntity loadCurrentUser(long userId) {
    UserEntity user = userMapper.selectById(userId);
    if (user == null) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    return user;
  }

  private void ensureUserCanLogin(UserEntity user) {
    if (user.getStatus() != null && user.getStatus() == STATUS_BANNED) {
      throw new ImException(ErrorCode.USER_BANNED);
    }
  }

  private TokenResponse issueTokens(long tenantId, long userId) {
    return new TokenResponse(
        jwtService.createAccessToken(tenantId, userId),
        jwtService.createRefreshToken(tenantId, userId),
        TOKEN_TYPE,
        jwtService.accessExpiresInSeconds(),
        jwtService.refreshExpiresInSeconds());
  }

  private UserProfileResponse toProfile(UserEntity user) {
    return new UserProfileResponse(
        user.getId(),
        user.getTenantId(),
        user.getAccount(),
        user.getNickname(),
        user.getAvatar(),
        user.getUserType(),
        user.getVerifiedType(),
        user.getStatus(),
        user.getCreatedAt());
  }

  private String normalizeAccount(String account) {
    return account == null ? "" : account.trim();
  }

  private String normalizeNickname(String nickname, String account) {
    if (nickname == null || nickname.isBlank()) {
      return account;
    }
    return nickname.trim();
  }
}
