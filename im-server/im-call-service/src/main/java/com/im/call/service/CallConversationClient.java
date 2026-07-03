package com.im.call.service;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.grpc.GrpcMetadataKeys;
import com.im.common.tenant.TenantContext;
import com.im.common.trace.TraceContext;
import com.im.proto.body.ConvInfo;
import com.im.proto.rpc.ConnCtx;
import com.im.proto.rpc.ConversationRpcGrpc;
import com.im.proto.rpc.GetMembersReq;
import com.im.proto.rpc.GetMembersResp;
import com.im.proto.rpc.ResolveConvReq;
import com.im.proto.rpc.ResolveConvResp;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class CallConversationClient {

  private final ConversationRpcGrpc.ConversationRpcBlockingStub conversationStub;

  public CallConversationClient(
      @Qualifier("callConversationRpcBlockingStub")
      ConversationRpcGrpc.ConversationRpcBlockingStub conversationStub) {
    this.conversationStub = conversationStub;
  }

  public GroupCallTarget resolveGroupTarget(ConnCtx ctx, long groupId) {
    ResolveConvResp resolved = conversationStub
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata(ctx)))
        .resolveConv(ResolveConvReq.newBuilder()
            .setFromUserId(ctx.getUserId())
            .setGroupId(groupId)
            .build());
    if (resolved.getCode() != ErrorCode.OK.code() || !resolved.hasConv()) {
      ErrorCode code = ErrorCode.fromCode(resolved.getCode()).orElse(ErrorCode.INTERNAL_ERROR);
      throw new ImException(code);
    }
    ConvInfo conv = resolved.getConv();
    GetMembersResp members = conversationStub
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata(ctx)))
        .getMembers(GetMembersReq.newBuilder()
            .setConvId(conv.getConvId())
            .build());
    List<Long> memberIds = members.getUserIdsList().stream()
        .filter(id -> id > 0 && id != ctx.getUserId())
        .distinct()
        .toList();
    return new GroupCallTarget(conv.getGroupId(), conv.getConvId(), memberIds);
  }

  private Metadata metadata(ConnCtx ctx) {
    Metadata metadata = new Metadata();
    metadata.put(GrpcMetadataKeys.TENANT_ID, String.valueOf(TenantContext.requiredTenantId()));
    String traceId = ctx.getTraceId();
    if (traceId == null || traceId.isBlank()) {
      traceId = TraceContext.currentTraceId().orElse("");
    }
    if (!traceId.isBlank()) {
      metadata.put(GrpcMetadataKeys.TRACE_ID, traceId);
    }
    metadata.put(GrpcMetadataKeys.CALLER, "im-call-service");
    return metadata;
  }

  public record GroupCallTarget(long groupId, long convId, List<Long> memberUserIds) {
  }
}
