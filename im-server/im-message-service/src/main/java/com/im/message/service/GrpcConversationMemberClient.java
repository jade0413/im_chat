package com.im.message.service;

import com.im.common.grpc.GrpcMetadataKeys;
import com.im.common.tenant.TenantContext;
import com.im.common.trace.TraceContext;
import com.im.proto.body.ConvInfo;
import com.im.proto.rpc.ConversationRpcGrpc;
import com.im.proto.rpc.GetMembersReq;
import com.im.proto.rpc.ListMemberConvsReq;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GrpcConversationMemberClient implements ConversationMemberClient {

  private final ConversationRpcGrpc.ConversationRpcBlockingStub conversationStub;

  public GrpcConversationMemberClient(ConversationRpcGrpc.ConversationRpcBlockingStub conversationStub) {
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
  public List<ConvInfo> listMemberConvs(long userId) {
    return conversationStub
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata()))
        .listMemberConvs(ListMemberConvsReq.newBuilder()
            .setUserId(userId)
            .setLimit(100)
            .build())
        .getConvsList();
  }

  private Metadata metadata() {
    Metadata metadata = new Metadata();
    metadata.put(GrpcMetadataKeys.TENANT_ID, String.valueOf(TenantContext.requiredTenantId()));
    TraceContext.currentTraceId().ifPresent(traceId -> metadata.put(GrpcMetadataKeys.TRACE_ID, traceId));
    metadata.put(GrpcMetadataKeys.CALLER, "im-message-service");
    return metadata;
  }
}
