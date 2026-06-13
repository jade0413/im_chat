package com.im.message.service;

import com.im.common.grpc.GrpcMetadataKeys;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.common.trace.TraceContext;
import com.im.proto.body.ConvInfo;
import com.im.proto.rpc.ConversationRpcGrpc;
import com.im.proto.rpc.GetMemberConvReq;
import com.im.proto.rpc.GetMemberConvResp;
import com.im.proto.rpc.GetMembersReq;
import com.im.proto.rpc.ListMemberConvsReq;
import com.im.proto.rpc.ListMemberConvsResp;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class GrpcConversationMemberClient implements ConversationMemberClient {

  private final ConversationRpcGrpc.ConversationRpcBlockingStub conversationStub;

  public GrpcConversationMemberClient(
      @Qualifier("conversationRpcBlockingStub")
      ConversationRpcGrpc.ConversationRpcBlockingStub conversationStub) {
    this.conversationStub = conversationStub;
  }

  @Override
  public List<Long> getMemberUserIds(long conversationId) {
    return conversationStub
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata()))
        .getMembers(GetMembersReq.newBuilder().setConvId(conversationId).build())
        .getUserIdsList();
  }

  @Override
  public ConvInfo getMemberConv(long userId, long conversationId) {
    GetMemberConvResp response = conversationStub
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata()))
        .getMemberConv(GetMemberConvReq.newBuilder()
            .setUserId(userId)
            .setConvId(conversationId)
            .build());
    if (response.getCode() != ErrorCode.OK.code()) {
      throw new ImException(ErrorCode.fromCode(response.getCode()).orElse(ErrorCode.INTERNAL_ERROR));
    }
    return response.getConv();
  }

  @Override
  public ConversationListPage listMemberConvs(long userId, long convListVersion) {
    ListMemberConvsResp response = conversationStub
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata()))
        .listMemberConvs(ListMemberConvsReq.newBuilder()
            .setUserId(userId)
            .setLimit(100)
            .setConvListVersion(convListVersion)
            .build());
    return new ConversationListPage(
        response.getConvsList(),
        response.getHasMore(),
        response.getConvListVersion());
  }

  private Metadata metadata() {
    Metadata metadata = new Metadata();
    metadata.put(GrpcMetadataKeys.TENANT_ID, String.valueOf(TenantContext.requiredTenantId()));
    TraceContext.currentTraceId().ifPresent(traceId -> metadata.put(GrpcMetadataKeys.TRACE_ID, traceId));
    metadata.put(GrpcMetadataKeys.CALLER, "im-message-service");
    return metadata;
  }
}
