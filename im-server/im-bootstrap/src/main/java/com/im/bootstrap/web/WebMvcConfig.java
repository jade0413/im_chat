package com.im.bootstrap.web;

import com.im.common.auth.JwtAccessTokenVerifier;
import com.im.common.auth.JwtAuthInterceptor;
import com.im.common.auth.TokenVersionService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册全局 JWT 鉴权拦截器。
 * 排除路径：
 *   - /api/v1/auth/**        登录 / 注册 / 刷新 token（无需鉴权）
 *   - /api/v1/cs/widget/**   访客 widget 接入端点（由 CsSessionController 自行鉴权）
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  private final JwtAccessTokenVerifier tokenVerifier;
  private final TokenVersionService tokenVersionService;

  public WebMvcConfig(JwtAccessTokenVerifier tokenVerifier,
      TokenVersionService tokenVersionService) {
    this.tokenVerifier = tokenVerifier;
    this.tokenVersionService = tokenVersionService;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new JwtAuthInterceptor(tokenVerifier, tokenVersionService))
        .addPathPatterns("/api/**")
        .excludePathPatterns(
            "/api/v1/auth/**",
            "/api/v1/cs/widget/**");
  }
}
