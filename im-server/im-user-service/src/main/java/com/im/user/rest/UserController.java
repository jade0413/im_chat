package com.im.user.rest;

import com.im.common.web.ApiResponse;
import com.im.user.dto.UserProfileResponse;
import com.im.user.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final AuthService authService;

  public UserController(AuthService authService) {
    this.authService = authService;
  }

  @GetMapping("/me")
  public ApiResponse<UserProfileResponse> me(@RequestHeader("Authorization") String authorization) {
    return ApiResponse.ok(authService.currentUser(AuthController.bearerToken(authorization)));
  }
}
