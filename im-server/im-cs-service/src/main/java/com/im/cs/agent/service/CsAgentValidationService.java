package com.im.cs.agent.service;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.proto.rpc.CheckIsAgentReq;
import com.im.proto.rpc.CheckIsAgentResp;
import com.im.proto.rpc.UserRpcGrpc;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 坐席权限校验（T35）。
 *
 * <p>通过 gRPC 调用 user-service 检查 user.is_agent=1（D34）。
 * 独立成 Service 便于缓存层面扩展（二阶段可加 Redis 缓存减少 gRPC 调用）。
 */
@Service
public class CsAgentValidationService {

  private final UserRpcGrpc.UserRpcBlockingStub userRpcStub;

  public CsAgentValidationService(
      @Qualifier("csUserRpcBlockingStub")
      UserRpcGrpc.UserRpcBlockingStub userRpcStub) {
    this.userRpcStub = userRpcStub;
  }

  /**
   * 校验当前用户是否为坐席，否则抛 NO_PERMISSION（403）。
   *
   * @param userId 当前登录用户 ID（来自 UserContext）
   * @throws ImException NO_PERMISSION 如果 is_agent=0
   */
  public void requireAgent(long userId) {
    CheckIsAgentResp resp = userRpcStub.checkIsAgent(
        CheckIsAgentReq.newBuilder().setUserId(userId).build());
    if (!resp.getIsAgent()) {
      throw new ImException(ErrorCode.NO_PERMISSION, "当前用户无坐席权限，userId=" + userId);
    }
  }
}
