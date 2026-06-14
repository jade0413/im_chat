package com.im.user.grpcapi;

import com.im.common.error.ErrorCode;
import com.im.proto.rpc.CheckAgentAvailabilityReq;
import com.im.proto.rpc.CheckAgentAvailabilityResp;
import com.im.proto.rpc.GetOnlineAgentIdsReq;
import com.im.proto.rpc.GetOnlineAgentIdsResp;
import com.im.proto.rpc.CheckIsAgentReq;
import com.im.proto.rpc.CheckIsAgentResp;
import com.im.proto.rpc.CheckRelationReq;
import com.im.proto.rpc.CheckRelationResp;
import com.im.proto.rpc.IssueVisitorTokenReq;
import com.im.proto.rpc.IssueVisitorTokenResp;
import com.im.proto.rpc.ProvisionVisitorUserReq;
import com.im.proto.rpc.ProvisionVisitorUserResp;
import com.im.proto.rpc.UpdateAgentStatusReq;
import com.im.proto.rpc.UpdateAgentStatusResp;
import com.im.proto.rpc.UserRpcGrpc;
import com.im.user.dto.TokenResponse;
import com.im.user.service.AgentService;
import com.im.user.service.RelationService;
import com.im.user.service.RelationService.RelationCheckResult;
import com.im.user.service.VisitorUserService;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

@Service
public class UserRpcGrpcService extends UserRpcGrpc.UserRpcImplBase {

  private final RelationService relationService;
  private final VisitorUserService visitorUserService;
  private final AgentService agentService;

  public UserRpcGrpcService(RelationService relationService,
      VisitorUserService visitorUserService,
      AgentService agentService) {
    this.relationService = relationService;
    this.visitorUserService = visitorUserService;
    this.agentService = agentService;
  }

  @Override
  public void checkRelation(CheckRelationReq request,
      StreamObserver<CheckRelationResp> responseObserver) {
    RelationCheckResult result = relationService.check(
        request.getFromUserId(), request.getToUserId());
    responseObserver.onNext(CheckRelationResp.newBuilder()
        .setBlocked(result.blockedByPeer())
        .setFriendRequiredUnmet(result.friendRequiredUnmet())
        .build());
    responseObserver.onCompleted();
  }

  /** 创建访客用户（T31）。TenantContext 由 gRPC metadata 拦截器注入。 */
  @Override
  public void provisionVisitorUser(ProvisionVisitorUserReq request,
      StreamObserver<ProvisionVisitorUserResp> responseObserver) {
    try {
      long userId = visitorUserService.createVisitorUser(
          request.getTenantId(), request.getDisplayName());
      responseObserver.onNext(ProvisionVisitorUserResp.newBuilder()
          .setUserId(userId)
          .setDisplayName(request.getDisplayName())
          .build());
    } catch (Exception ex) {
      responseObserver.onError(
          io.grpc.Status.INTERNAL.withDescription(ErrorCode.INTERNAL_ERROR.defaultMessage())
              .asRuntimeException());
      return;
    }
    responseObserver.onCompleted();
  }

  /** 为访客用户签发 JWT（T31）。 */
  @Override
  public void issueVisitorToken(IssueVisitorTokenReq request,
      StreamObserver<IssueVisitorTokenResp> responseObserver) {
    try {
      TokenResponse token = visitorUserService.issueVisitorToken(
          request.getTenantId(), request.getUserId());
      responseObserver.onNext(IssueVisitorTokenResp.newBuilder()
          .setAccessToken(token.accessToken())
          .setRefreshToken(token.refreshToken())
          .setExpiresIn(token.expiresIn())
          .build());
    } catch (Exception ex) {
      responseObserver.onError(
          io.grpc.Status.INTERNAL.withDescription(ErrorCode.INTERNAL_ERROR.defaultMessage())
              .asRuntimeException());
      return;
    }
    responseObserver.onCompleted();
  }

  /** 检查用户是否为坐席（is_agent=1）（T35）。 */
  @Override
  public void checkIsAgent(CheckIsAgentReq request,
      StreamObserver<CheckIsAgentResp> responseObserver) {
    AgentService.AgentInfo info = agentService.getAgentInfo(request.getUserId());
    responseObserver.onNext(CheckIsAgentResp.newBuilder()
        .setIsAgent(info.isAgent())
        .setAgentStatus(info.agentStatus())
        .build());
    responseObserver.onCompleted();
  }

  /** 坐席切换在线状态（D35/T35）。 */
  @Override
  public void updateAgentStatus(UpdateAgentStatusReq request,
      StreamObserver<UpdateAgentStatusResp> responseObserver) {
    try {
      agentService.updateStatus(request.getUserId(), request.getAgentStatus());
      responseObserver.onNext(UpdateAgentStatusResp.newBuilder()
          .setCode(ErrorCode.OK.code()).build());
    } catch (Exception ex) {
      responseObserver.onNext(UpdateAgentStatusResp.newBuilder()
          .setCode(ErrorCode.INTERNAL_ERROR.code()).build());
    }
    responseObserver.onCompleted();
  }

  /** 查询租户内是否有坐席在线（Widget availability，T36）。 */
  @Override
  public void checkAgentAvailability(CheckAgentAvailabilityReq request,
      StreamObserver<CheckAgentAvailabilityResp> responseObserver) {
    int count = agentService.countOnlineAgents(request.getTenantId());
    responseObserver.onNext(CheckAgentAvailabilityResp.newBuilder()
        .setAvailable(count > 0)
        .setOnlineAgentCount(count)
        .build());
    responseObserver.onCompleted();
  }

  /** 返回租户所有 online/busy 坐席的 user_id（D33: CS open 会话推送扇出）。 */
  @Override
  public void getOnlineAgentIds(GetOnlineAgentIdsReq request,
      StreamObserver<GetOnlineAgentIdsResp> responseObserver) {
    responseObserver.onNext(GetOnlineAgentIdsResp.newBuilder()
        .addAllAgentIds(agentService.getOnlineAgentIds(request.getTenantId()))
        .build());
    responseObserver.onCompleted();
  }
}
