package com.im.common.auth;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 统一 JWT 鉴权拦截器。
 * 在 WebMvcConfig 中注册，排除 /api/v1/auth/** 和 /api/v1/cs/widget/sessions。
 *
 * <p>流程：
 * <ol>
 *   <li>提取 Bearer token</li>
 *   <li>验证签名 + 过期时间，提取 claims</li>
 *   <li>校验 tenantId 与 TenantContextFilter 设置的 TenantContext 一致</li>
 *   <li>若 platformClass 非空则验证 tokenVersion（互踢时版本不匹配即失效）</li>
 *   <li>将 userId 写入 UserContext</li>
 * </ol>
 */
public class JwtAuthInterceptor implements HandlerInterceptor {

  private final JwtAccessTokenVerifier tokenVerifier;
  private final TokenVersionService tokenVersionService;

  public JwtAuthInterceptor(JwtAccessTokenVerifier tokenVerifier,
      TokenVersionService tokenVersionService) {
    this.tokenVerifier = tokenVerifier;
    this.tokenVersionService = tokenVersionService;
  }

  @Override
  public boolean preHandle(HttpServletRequest request,
      HttpServletResponse response,
      Object handler) {
    String authorization = request.getHeader("Authorization");
    String token = BearerTokenExtractor.extract(authorization);
    AuthTokenClaims claims = tokenVerifier.verifyAccessToken(token);

    long tenantId = TenantContext.requiredTenantId();
    if (claims.tenantId() != tenantId) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }

    if (claims.platformClass() != null && !claims.platformClass().isBlank()) {
      tokenVersionService.ensureCurrent(
          tenantId, claims.userId(), claims.platformClass(), claims.tokenVersion());
    }

    UserContext.set(claims.userId());
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      Exception ex) {
    UserContext.clear();
  }
}
