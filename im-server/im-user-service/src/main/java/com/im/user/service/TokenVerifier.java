package com.im.user.service;

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

  public TokenVerifier(JwtService jwtService, UserMapper userMapper,
      GatewayAuthProperties gatewayAuthProperties) {
    this.jwtService = jwtService;
    this.userMapper = userMapper;
    this.gatewayAuthProperties = gatewayAuthProperties;
  }

  public VerifyTokenResult verify(String token, long tenantId) {
    if (tenantId <= 0) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    TokenClaims claims = jwtService.verifyAccessToken(token);
    if (claims.tenantId() != tenantId) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    try {
      return TenantContext.callWithTenant(tenantId, () -> verifyUser(claims.userId()));
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
