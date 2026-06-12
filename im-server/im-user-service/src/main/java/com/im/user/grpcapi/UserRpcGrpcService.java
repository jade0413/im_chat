package com.im.user.grpcapi;

import com.im.proto.rpc.CheckRelationReq;
import com.im.proto.rpc.CheckRelationResp;
import com.im.proto.rpc.UserRpcGrpc;
import com.im.user.service.RelationService;
import com.im.user.service.RelationService.RelationCheckResult;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

@Service
public class UserRpcGrpcService extends UserRpcGrpc.UserRpcImplBase {

  private final RelationService relationService;

  public UserRpcGrpcService(RelationService relationService) {
    this.relationService = relationService;
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
}
