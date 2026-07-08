package com.im.push.rest;

import com.im.common.auth.UserContext;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.common.web.ApiResponse;
import com.im.push.dto.PresenceResponse;
import com.im.push.route.OnlineRouteRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 普通用户在线状态查询。
 *
 * <p>只读现有在线 route 表；online=false 表示没有可用在线连接，前端不展示离线标签。
 */
@RestController
@RequestMapping("/api/v1/presence")
public class PresenceController {

  private final OnlineRouteRepository routeRepository;

  public PresenceController(OnlineRouteRepository routeRepository) {
    this.routeRepository = routeRepository;
  }

  @GetMapping("/users/{userId}")
  public ApiResponse<PresenceResponse> userPresence(@PathVariable long userId) {
    UserContext.requiredUserId();
    if (userId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "userId must be positive");
    }
    long tenantId = TenantContext.requiredTenantId();
    boolean online = !routeRepository.findAll(tenantId, userId).isEmpty();
    return ApiResponse.ok(new PresenceResponse(userId, online));
  }
}
