package com.im.message.grpcapi;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.message.service.MessageQueryService;
import com.im.message.service.MessageRevokeService;
import com.im.proto.rpc.MessageRpcGrpc;
import com.im.proto.rpc.PullMsgsReq;
import com.im.proto.rpc.PullMsgsResp;
import com.im.proto.rpc.RevokeMsgReq;
import com.im.proto.rpc.RevokeMsgResp;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

@Service
public class MessageGrpcService extends MessageRpcGrpc.MessageRpcImplBase {

  private final MessageQueryService messageQueryService;
  private final MessageRevokeService messageRevokeService;

  public MessageGrpcService(MessageQueryService messageQueryService,
      MessageRevokeService messageRevokeService) {
    this.messageQueryService = messageQueryService;
    this.messageRevokeService = messageRevokeService;
  }

  @Override
  public void pullMsgs(PullMsgsReq request, StreamObserver<PullMsgsResp> responseObserver) {
    PullMsgsResp response = PullMsgsResp.newBuilder()
        .addAllMsgs(messageQueryService.pullForRpc(request))
        .build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void revokeMsg(RevokeMsgReq request, StreamObserver<RevokeMsgResp> responseObserver) {
    RevokeMsgResp response;
    try {
      messageRevokeService.revoke(
          request.getConvId(),
          request.getSeq(),
          request.getReason(),
          request.getOperatorUserId());
      response = RevokeMsgResp.newBuilder()
          .setCode(ErrorCode.OK.code())
          .build();
    } catch (ImException ex) {
      response = RevokeMsgResp.newBuilder()
          .setCode(ex.errorCode().code())
          .build();
    } catch (Exception ex) {
      response = RevokeMsgResp.newBuilder()
          .setCode(ErrorCode.INTERNAL_ERROR.code())
          .build();
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }
}
