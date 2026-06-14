package com.im.user.service;

import com.im.common.auth.TokenVersionService;
import com.im.common.device.PlatformClass;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.user.config.GatewayAuthProperties;
import com.im.user.dao.entity.UserEntity;
import com.im.user.dao.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class TokenVerifier {

  private static final int STATUS_BANNED = 3;

  private final JwtService jwtService;
  private final UserMapper userMapper;
  private final GatewayAuthProperties gatewayAuthProperties;
  private final TokenVersionService tokenVersionService;

  public TokenVerifier(JwtService jwtService, UserMapper userMapper,
      GatewayAuthProperties gatewayAuthProperties, TokenVersionService tokenVersionService) {
    this.jwtService = jwtService;
    this.userMapper = userMapper;
    this.gatewayAuthProperties = gatewayAuthProperties;
    this.tokenVersionService = tokenVersionService;
  }

  public VerifyTokenResult verify(String token, long tenantId, int platform) {
    if (tenantId <= 0) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    TokenClaims claims = jwtService.verifyAccessToken(token);
    if (claims.tenantId() != tenantId) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    if (claims.platformClass() == null || claims.platformClass().isBlank()) {
      return verifyTenantUser(tenantId, claims.userId());
    }
    String platformClass = PlatformClass.fromPlatform(platform).key();
    if (!platformClass.equals(claims.platformClass())) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    tokenVersionService.ensureCurrent(tenantId, claims.userId(), platformClass, claims.tokenVersion());
    return verifyTenantUser(tenantId, claims.userId());
  }

  private VerifyTokenResult verifyTenantUser(long tenantId, long userId) {
    try {
      return TenantContext.callWithTenant(tenantId, () -> verifyUser(userId));
    } catch (ImException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "failed to verify token");
    }
  }

  private VerifyTokenResult verifyUser(long userId) {
    UserEntity user = userMapper.selectById(userId);
    if (user == null) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    if (user.getStatus() != null && user.getStatus() == STATUS_BANNED) {
      throw new ImException(ErrorCode.USER_BANNED);
    }
    return new VerifyTokenResult(user.getId(), gatewayAuthProperties.heartbeatIntervalSec());
  }
}
