package com.im.user.rest;

import com.im.common.auth.UserContext;
import com.im.common.web.ApiResponse;
import com.im.user.dto.UpdateAgentStatusRequest;
import com.im.user.dto.UpdateProfileRequest;
import com.im.user.dto.UpdateUsernameRequest;
import com.im.user.dto.UserProfileResponse;
import com.im.user.dto.UserPublicProfileResponse;
import com.im.user.service.AgentService;
import com.im.user.service.AuthService;
import com.im.user.service.UsernameService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final AuthService authService;
  private final AgentService agentService;
  private final UsernameService usernameService;

  public UserController(AuthService authService, AgentService agentService,
      UsernameService usernameService) {
    this.authService = authService;
    this.agentService = agentService;
    this.usernameService = usernameService;
  }

  /** 当前登录用户完整资料（含账号）。 */
  @GetMapping("/me")
  public ApiResponse<UserProfileResponse> me() {
    return ApiResponse.ok(authService.getProfile(UserContext.requiredUserId()));
  }

  /**
   * 修改个人资料：昵称（展示名，可改）+ 头像（可选）。
   *
   * <pre>
   * PUT /api/v1/users/me/profile
   * Body: {"nickname": "Jade", "avatar": "https://..."}   // avatar 可省略
   * </pre>
   */
  @PutMapping("/me/profile")
  public ApiResponse<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
    authService.updateProfile(UserContext.requiredUserId(), request.nickname(), request.avatar());
    return ApiResponse.ok(null);
  }

  /** 查询其他用户公开资料（不含账号）。 */
  @GetMapping("/{userId}")
  public ApiResponse<UserPublicProfileResponse> publicProfile(@PathVariable long userId) {
    return ApiResponse.ok(authService.getPublicProfile(userId));
  }

  /**
   * 按关键字搜索用户（昵称/账号前缀匹配），最多 20 条（D17 开放式单聊）。
   *
   * <pre>GET /api/v1/users/search?keyword=jade</pre>
   */
  @GetMapping("/search")
  public ApiResponse<List<UserPublicProfileResponse>> search(
      @RequestParam @NotBlank String keyword) {
    return ApiResponse.ok(authService.searchUsers(UserContext.requiredUserId(), keyword));
  }

  /**
   * 批量查询用户公开资料（供前端历史消息填充昵称）。
   *
   * <pre>GET /api/v1/users/batch?ids=1001,1002,1003</pre>
   */
  @GetMapping("/batch")
  public ApiResponse<List<UserPublicProfileResponse>> batch(
      @RequestParam List<Long> ids) {
    return ApiResponse.ok(authService.batchGetUsers(ids));
  }

  /**
   * 坐席切换在线状态（D35）。
   *
   * <pre>
   * PATCH /api/v1/users/me/agent-status
   * Body: {"agentStatus": 1}   // 0=offline 1=online 2=busy
   * </pre>
   */
  @PatchMapping("/me/agent-status")
  public ApiResponse<Void> updateAgentStatus(
      @Valid @RequestBody UpdateAgentStatusRequest request) {
    agentService.updateStatus(UserContext.requiredUserId(), request.agentStatus());
    return ApiResponse.ok(null);
  }

  /**
   * 设置/修改唯一用户名（D42，可分享的对外加好友标识）。
   *
   * <pre>
   * PUT /api/v1/users/me/username
   * Body: {"username": "jade_im"}   // ^[a-z][a-z0-9_]{5,31}$，租户内唯一
   * </pre>
   */
  @PutMapping("/me/username")
  public ApiResponse<Void> updateUsername(
      @Valid @RequestBody UpdateUsernameRequest request) {
    usernameService.setUsername(UserContext.requiredUserId(), request.username());
    return ApiResponse.ok(null);
  }
}
