package com.im.push.service;

import com.im.common.grpc.GrpcMetadataKeys;
import com.im.common.tenant.TenantContext;
import com.im.common.trace.TraceContext;
import com.im.proto.rpc.ConversationRpcGrpc;
import com.im.proto.rpc.GetMembersReq;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service("pushConversationMemberClient")
public class GrpcConversationMemberClient implements ConversationMemberClient {

  private final ConversationRpcGrpc.ConversationRpcBlockingStub conversationStub;

  public GrpcConversationMemberClient(
      @Qualifier("pushConversationRpcBlockingStub")
      ConversationRpcGrpc.ConversationRpcBlockingStub pushConversationRpcBlockingStub) {
    this.conversationStub = pushConversationRpcBlockingStub;
  }

  @Override
  public List<Long> getMemberUserIds(long conversationId) {
    return conversationStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata()))
        .getMembers(GetMembersReq.newBuilder()
            .setConvId(conversationId)
            .setOnlineHint(true)
            .build())
        .getUserIdsList();
  }

  private Metadata metadata() {
    Metadata metadata = new Metadata();
    metadata.put(GrpcMetadataKeys.TENANT_ID, String.valueOf(TenantContext.requiredTenantId()));
    TraceContext.currentTraceId().ifPresent(traceId -> metadata.put(GrpcMetadataKeys.TRACE_ID, traceId));
    metadata.put(GrpcMetadataKeys.CALLER, "im-push-service");
    return metadata;
  }
}
