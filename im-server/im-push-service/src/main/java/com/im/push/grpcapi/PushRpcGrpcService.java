package com.im.push.grpcapi;

import com.im.common.tenant.TenantContext;
import com.im.proto.rpc.KickUserReq;
import com.im.proto.rpc.PushRpcGrpc;
import com.im.proto.rpc.PushToUsersReq;
import com.im.proto.rpc.PushToUsersResp;
import com.im.push.service.PushDispatchService;
import com.im.push.service.PushResult;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

@Service
public class PushRpcGrpcService extends PushRpcGrpc.PushRpcImplBase {

  private final PushDispatchService pushDispatchService;

  public PushRpcGrpcService(PushDispatchService pushDispatchService) {
    this.pushDispatchService = pushDispatchService;
  }

  @Override
  public void pushToUsers(PushToUsersReq request, StreamObserver<PushToUsersResp> responseObserver) {
    PushResult result = pushDispatchService.pushToUsers(
        TenantContext.requiredTenantId(),
        request.getUserIdsList(),
        request.getCmd(),
        request.getBody().toByteArray(),
        request.getNeedAck(),
        request.getExcludeUserId(),
        request.getExcludeConnId());
    complete(responseObserver, result);
  }

  @Override
  public void kickUser(KickUserReq request, StreamObserver<PushToUsersResp> responseObserver) {
    PushResult result = pushDispatchService.kickUser(
        TenantContext.requiredTenantId(),
        request.getUserId(),
        request.getPlatform(),
        request.getReason());
    complete(responseObserver, result);
  }

  private void complete(StreamObserver<PushToUsersResp> responseObserver, PushResult result) {
    responseObserver.onNext(PushToUsersResp.newBuilder()
        .setOnlineCount(result.onlineCount())
        .setOfflineCount(result.offlineCount())
        .build());
    responseObserver.onCompleted();
  }
}
