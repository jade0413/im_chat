package com.im.message.grpcapi;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.message.service.MessageQueryService;
import com.im.message.service.MessageRevokeService;
import com.im.message.service.SystemNotificationService;
import com.im.proto.rpc.MessageRpcGrpc;
import com.im.proto.rpc.PullMsgsReq;
import com.im.proto.rpc.PullMsgsResp;
import com.im.proto.rpc.RevokeMsgReq;
import com.im.proto.rpc.RevokeMsgResp;
import com.im.proto.rpc.SendSystemNotificationReq;
import com.im.proto.rpc.SendSystemNotificationResp;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

@Service
public class MessageGrpcService extends MessageRpcGrpc.MessageRpcImplBase {

  private final MessageQueryService messageQueryService;
  private final MessageRevokeService messageRevokeService;
  private final SystemNotificationService systemNotificationService;

  public MessageGrpcService(MessageQueryService messageQueryService,
      MessageRevokeService messageRevokeService,
      SystemNotificationService systemNotificationService) {
    this.messageQueryService = messageQueryService;
    this.messageRevokeService = messageRevokeService;
    this.systemNotificationService = systemNotificationService;
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
  public void sendSystemNotification(SendSystemNotificationReq request,
      StreamObserver<SendSystemNotificationResp> responseObserver) {
    SendSystemNotificationResp response;
    try {
      long seq = systemNotificationService.send(
          request.getTenantId(),
          request.getToUserId(),
          request.getEventType(),
          request.getPayload());
      response = SendSystemNotificationResp.newBuilder()
          .setCode(ErrorCode.OK.code())
          .setSeq(seq)
          .build();
    } catch (ImException ex) {
      response = SendSystemNotificationResp.newBuilder()
          .setCode(ex.errorCode().code())
          .build();
    } catch (Exception ex) {
      response = SendSystemNotificationResp.newBuilder()
          .setCode(ErrorCode.INTERNAL_ERROR.code())
          .build();
    }
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
