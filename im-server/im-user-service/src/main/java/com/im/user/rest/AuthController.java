package com.im.user.rest;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.web.ApiResponse;
import com.im.user.dto.LoginRequest;
import com.im.user.dto.RefreshRequest;
import com.im.user.dto.RegisterRequest;
import com.im.user.dto.TokenResponse;
import com.im.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ApiResponse.ok(authService.register(request));
  }

  @PostMapping("/login")
  public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.ok(authService.login(request));
  }

  @PostMapping("/refresh")
  public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    return ApiResponse.ok(authService.refresh(request.refreshToken()));
  }

  static String bearerToken(String authorization) {
    if (authorization == null || authorization.isBlank()) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    String prefix = "Bearer ";
    if (!authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    String token = authorization.substring(prefix.length()).trim();
    if (token.isBlank()) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    return token;
  }
}
