package com.im.cs.widget.service;

import com.im.common.tenant.TenantContext;
import com.im.cs.config.CsGrpcMetadata;
import com.im.cs.widget.CsConstants;
import com.im.cs.widget.dao.entity.WidgetConfigEntity;
import com.im.cs.widget.dao.mapper.WidgetConfigMapper;
import com.im.cs.widget.dto.AgentAvailabilityResponse;
import com.im.cs.widget.dto.WidgetConfigResponse;
import com.im.proto.rpc.CheckAgentAvailabilityReq;
import com.im.proto.rpc.CheckAgentAvailabilityResp;
import com.im.proto.rpc.UserRpcGrpc;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Widget 公开接口服务（D37/T36）。
 *
 * <p>这两个接口无需访客 JWT，由 X-Tenant-Id header + TenantInterceptor 标识租户。
 * JwtAuthInterceptor 已将 /api/v1/cs/widget/** 排除在鉴权之外。
 */
@Service
public class WidgetService {

  private final WidgetConfigMapper widgetConfigMapper;
  private final UserRpcGrpc.UserRpcBlockingStub userRpcStub;

  public WidgetService(WidgetConfigMapper widgetConfigMapper,
      @Qualifier("csUserRpcBlockingStub")
      UserRpcGrpc.UserRpcBlockingStub userRpcStub) {
    this.widgetConfigMapper = widgetConfigMapper;
    this.userRpcStub = userRpcStub;
  }

  /**
   * 获取租户 Widget 配置。若未配置，返回默认值（D37 硬编码默认）。
   */
  public WidgetConfigResponse getConfig() {
    long tenantId = TenantContext.requiredTenantId();
    WidgetConfigEntity config = widgetConfigMapper.findByTenant(tenantId);
    if (config == null) {
      // 租户尚未自定义配置，返回 CsConstants 默认值（D37）
      return new WidgetConfigResponse(
          CsConstants.WIDGET_DEFAULT_COLOR,
          CsConstants.WIDGET_DEFAULT_WELCOME_MSG,
          CsConstants.WIDGET_DEFAULT_OFFLINE_MSG,
          CsConstants.WIDGET_DEFAULT_DISPLAY_NAME,
          CsConstants.WIDGET_DEFAULT_POSITION,
          true  // powered_by 默认开启
      );
    }
    return new WidgetConfigResponse(
        config.getColor(),
        config.getWelcomeMsg(),
        config.getOfflineMsg(),
        config.getDisplayName(),
        config.getPosition(),
        Integer.valueOf(1).equals(config.getPoweredBy())
    );
  }

  /**
   * 检查租户是否有坐席在线（Widget 显示在线/离线状态用）。
   * 通过 gRPC 查询 user-service 的 user.agent_status in(online,busy) 坐席数（D35）。
   */
  public AgentAvailabilityResponse checkAvailability() {
    long tenantId = TenantContext.requiredTenantId();
    CheckAgentAvailabilityResp resp = CsGrpcMetadata.withMetadata(userRpcStub).checkAgentAvailability(
        CheckAgentAvailabilityReq.newBuilder().setTenantId(tenantId).build());
    return new AgentAvailabilityResponse(resp.getAvailable(), resp.getOnlineAgentCount());
  }
}
