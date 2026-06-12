package com.im.message.grpcapi;

import com.im.common.error.ErrorCode;
import com.im.message.service.MessageQueryService;
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

  public MessageGrpcService(MessageQueryService messageQueryService) {
    this.messageQueryService = messageQueryService;
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
    responseObserver.onNext(RevokeMsgResp.newBuilder()
        .setCode(ErrorCode.VALIDATION_FAILED.code())
        .build());
    responseObserver.onCompleted();
  }
}
