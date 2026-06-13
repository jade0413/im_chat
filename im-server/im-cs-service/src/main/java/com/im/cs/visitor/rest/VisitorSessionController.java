package com.im.cs.visitor.rest;

import com.im.common.web.ApiResponse;
import com.im.cs.visitor.dto.WidgetSessionRequest;
import com.im.cs.visitor.dto.WidgetSessionResponse;
import com.im.cs.visitor.service.VisitorSessionService;
import com.im.cs.widget.dto.AgentAvailabilityResponse;
import com.im.cs.widget.dto.WidgetConfigResponse;
import com.im.cs.widget.service.WidgetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Widget 公开接口（T31/T36）。
 *
 * <p>调用方：嵌入企业网站的 JS snippet / uni-app 访客端。
 *
 * <p>鉴权：全部免鉴权（JwtAuthInterceptor 已排除 /api/v1/cs/widget/**），
 * 由 {@code X-Tenant-Id} 请求头标识租户（TenantInterceptor 处理）。
 */
@RestController
@RequestMapping("/api/v1/cs/widget")
public class VisitorSessionController {

  private final VisitorSessionService visitorSessionService;
  private final WidgetService widgetService;

  public VisitorSessionController(VisitorSessionService visitorSessionService,
      WidgetService widgetService) {
    this.visitorSessionService = visitorSessionService;
    this.widgetService = widgetService;
  }

  /**
   * 访客进入 widget，返回访客 JWT 和 CS 会话 ID（T31）。
   *
   * <pre>
   * POST /api/v1/cs/widget/sessions
   * Header: X-Tenant-Id: {tenantId}
   * Body:   {"visitorToken": "uuid-from-localstorage"}
   * </pre>
   */
  @PostMapping("/sessions")
  public ApiResponse<WidgetSessionResponse> enterWidget(
      @Valid @RequestBody WidgetSessionRequest request) {
    return ApiResponse.ok(visitorSessionService.enter(request));
  }

  /**
   * 获取租户 Widget 外观配置（T36/D37）。
   * JS snippet 加载后先调此接口渲染 Widget 样式。
   *
   * <pre>
   * GET /api/v1/cs/widget/config
   * Header: X-Tenant-Id: {tenantId}
   * </pre>
   */
  @GetMapping("/config")
  public ApiResponse<WidgetConfigResponse> getConfig() {
    return ApiResponse.ok(widgetService.getConfig());
  }

  /**
   * 检查租户坐席是否在线（T36/D35）。
   * Widget 据此显示"在线"或"离线留言"状态。
   *
   * <pre>
   * GET /api/v1/cs/widget/availability
   * Header: X-Tenant-Id: {tenantId}
   * Response: {"available": true, "onlineAgentCount": 2}
   * </pre>
   */
  @GetMapping("/availability")
  public ApiResponse<AgentAvailabilityResponse> checkAvailability() {
    return ApiResponse.ok(widgetService.checkAvailability());
  }
}
