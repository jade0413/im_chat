package com.im.cs.agent.rest;

import com.im.common.auth.UserContext;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.web.ApiResponse;
import com.im.cs.agent.dto.UpdateAgentStatusRequest;
import com.im.cs.agent.service.CsAgentValidationService;
import com.im.cs.config.CsGrpcMetadata;
import com.im.cs.notify.PendingConvNotifier;
import com.im.cs.widget.CsConstants;
import com.im.proto.rpc.UpdateAgentStatusReq;
import com.im.proto.rpc.UpdateAgentStatusResp;
import com.im.proto.rpc.UserRpcGrpc;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 坐席在线状态（CS 工作台，T34/T35）。
 *
 * <p>切换状态委托 user 模块 {@code UserRpc.UpdateAgentStatus}（DB 列，D35）；
 * 当切到 <b>online</b> 时，触发 {@link PendingConvNotifier} 检查待接待会话并 App 内提醒（T35）。
 *
 * <pre>PUT /api/v1/cs/agent/status  Body: {"agentStatus": 1}  // 0=offline 1=online 2=busy</pre>
 */
@RestController
@RequestMapping("/api/v1/cs/agent")
public class CsAgentStatusController {

  private final CsAgentValidationService validationService;
  private final UserRpcGrpc.UserRpcBlockingStub userRpcStub;
  private final PendingConvNotifier pendingConvNotifier;

  public CsAgentStatusController(CsAgentValidationService validationService,
      @Qualifier("csUserRpcBlockingStub") UserRpcGrpc.UserRpcBlockingStub userRpcStub,
      PendingConvNotifier pendingConvNotifier) {
    this.validationService = validationService;
    this.userRpcStub = userRpcStub;
    this.pendingConvNotifier = pendingConvNotifier;
  }

  @PutMapping("/status")
  public ApiResponse<Void> updateStatus(@Valid @RequestBody UpdateAgentStatusRequest request) {
    long agentId = UserContext.requiredUserId();
    validationService.requireAgent(agentId);

    UpdateAgentStatusResp resp = CsGrpcMetadata.withMetadata(userRpcStub).updateAgentStatus(
        UpdateAgentStatusReq.newBuilder()
            .setUserId(agentId)
            .setAgentStatus(request.agentStatus())
            .build());
    if (resp.getCode() != ErrorCode.OK.code()) {
      throw new ImException(ErrorCode.fromCode(resp.getCode()).orElse(ErrorCode.INTERNAL_ERROR),
          "切换坐席状态失败 code=" + resp.getCode());
    }

    // 上线时检查待接待会话并提醒（T35，离线留言收口）
    if (request.agentStatus() == CsConstants.AGENT_STATUS_ONLINE) {
      pendingConvNotifier.notifyIfPending(agentId);
    }
    return ApiResponse.ok(null);
  }
}
