package com.im.conversation.grpcapi;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.conversation.service.ConversationService;
import com.im.proto.body.ConvInfo;
import com.im.proto.rpc.ConversationRpcGrpc;
import com.im.proto.rpc.GetMembersReq;
import com.im.proto.rpc.GetMembersResp;
import com.im.proto.rpc.GetMemberConvReq;
import com.im.proto.rpc.GetMemberConvResp;
import com.im.proto.rpc.ListMemberConvsReq;
import com.im.proto.rpc.ListMemberConvsResp;
import com.im.proto.rpc.ResolveConvReq;
import com.im.proto.rpc.ResolveConvResp;
import io.grpc.stub.StreamObserver;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConversationGrpcService extends ConversationRpcGrpc.ConversationRpcImplBase {

  private final ConversationService conversationService;

  public ConversationGrpcService(ConversationService conversationService) {
    this.conversationService = conversationService;
  }

  @Override
  public void resolveConv(ResolveConvReq request, StreamObserver<ResolveConvResp> responseObserver) {
    ResolveConvResp response;
    try {
      ConvInfo conv = conversationService.resolve(request);
      response = ResolveConvResp.newBuilder()
          .setCode(ErrorCode.OK.code())
          .setConv(conv)
          .build();
    } catch (ImException ex) {
      response = ResolveConvResp.newBuilder()
          .setCode(ex.errorCode().code())
          .build();
    } catch (Exception ex) {
      response = ResolveConvResp.newBuilder()
          .setCode(ErrorCode.INTERNAL_ERROR.code())
          .build();
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getMembers(GetMembersReq request, StreamObserver<GetMembersResp> responseObserver) {
    GetMembersResp response;
    try {
      List<Long> userIds = conversationService.getMemberUserIds(request.getConvId());
      response = GetMembersResp.newBuilder()
          .addAllUserIds(userIds)
          .build();
    } catch (Exception ex) {
      response = GetMembersResp.getDefaultInstance();
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getMemberConv(GetMemberConvReq request,
      StreamObserver<GetMemberConvResp> responseObserver) {
    GetMemberConvResp response;
    try {
      response = GetMemberConvResp.newBuilder()
          .setCode(ErrorCode.OK.code())
          .setConv(conversationService.getMemberConv(request.getUserId(), request.getConvId()))
          .build();
    } catch (ImException ex) {
      response = GetMemberConvResp.newBuilder()
          .setCode(ex.errorCode().code())
          .build();
    } catch (Exception ex) {
      response = GetMemberConvResp.newBuilder()
          .setCode(ErrorCode.INTERNAL_ERROR.code())
          .build();
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void listMemberConvs(ListMemberConvsReq request,
      StreamObserver<ListMemberConvsResp> responseObserver) {
    ListMemberConvsResp response;
    try {
      response = ListMemberConvsResp.newBuilder()
          .addAllConvs(conversationService.listMemberConvs(request.getUserId(), request.getLimit()))
          .build();
    } catch (Exception ex) {
      response = ListMemberConvsResp.getDefaultInstance();
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }
}
