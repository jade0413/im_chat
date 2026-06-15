package com.im.cs.widget.rest;

import com.im.common.auth.UserContext;
import com.im.common.web.ApiResponse;
import com.im.cs.agent.service.CsAgentValidationService;
import com.im.cs.widget.dto.UpdateWidgetConfigRequest;
import com.im.cs.widget.service.WidgetAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Widget 配置管理端（T35b）。
 *
 * <p>路径置于 {@code /api/v1/cs/admin/**} 而非公开的 {@code /api/v1/cs/widget/**}，
 * 因此走正常 JWT 鉴权；并要求坐席权限（MVP 无独立租户管理员角色前的门槛，D37）。
 *
 * <pre>PUT /api/v1/cs/admin/widget/config</pre>
 */
@RestController
@RequestMapping("/api/v1/cs/admin/widget")
public class WidgetAdminController {

  private final WidgetAdminService widgetAdminService;
  private final CsAgentValidationService agentValidationService;

  public WidgetAdminController(WidgetAdminService widgetAdminService,
      CsAgentValidationService agentValidationService) {
    this.widgetAdminService = widgetAdminService;
    this.agentValidationService = agentValidationService;
  }

  @PutMapping("/config")
  public ApiResponse<Void> updateConfig(@Valid @RequestBody UpdateWidgetConfigRequest request) {
    agentValidationService.requireAgent(UserContext.requiredUserId());
    widgetAdminService.updateConfig(request);
    return ApiResponse.ok(null);
  }
}
