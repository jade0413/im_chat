package com.im.message.service;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.grpc.GrpcMetadataKeys;
import com.im.common.tenant.TenantContext;
import com.im.common.trace.TraceContext;
import com.im.proto.body.ConvInfo;
import com.im.proto.body.MsgSend;
import com.im.proto.common.ConvType;
import com.im.proto.rpc.ConnCtx;
import com.im.proto.rpc.ConversationRpcGrpc;
import com.im.proto.rpc.ResolveConvReq;
import com.im.proto.rpc.ResolveConvResp;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class GrpcConversationResolver implements ConversationResolver {

  private final ConversationRpcGrpc.ConversationRpcBlockingStub conversationStub;

  public GrpcConversationResolver(
      @Qualifier("conversationRpcBlockingStub")
      ConversationRpcGrpc.ConversationRpcBlockingStub conversationStub) {
    this.conversationStub = conversationStub;
  }

  @Override
  public ConvInfo resolve(ConnCtx ctx, MsgSend request) {
    ResolveConvReq resolveRequest = toResolveRequest(ctx, request);
    ResolveConvResp response = conversationStub
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata(ctx)))
        .resolveConv(resolveRequest);
    if (response.getCode() != ErrorCode.OK.code()) {
      ErrorCode errorCode = ErrorCode.fromCode(response.getCode()).orElse(ErrorCode.INTERNAL_ERROR);
      throw new ImException(errorCode);
    }
    if (!response.hasConv() || response.getConv().getConvId() <= 0) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "conversation resolver returned empty conv");
    }
    ConvType type = response.getConv().getType();
    if (type != ConvType.C2C && type != ConvType.GROUP && type != ConvType.CS_SESSION) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "unsupported conversation type");
    }
    return response.getConv();
  }

  private ResolveConvReq toResolveRequest(ConnCtx ctx, MsgSend request) {
    ResolveConvReq.Builder builder = ResolveConvReq.newBuilder()
        .setFromUserId(ctx.getUserId());
    return switch (request.getTargetCase()) {
      case TO_USER_ID -> builder.setToUserId(request.getToUserId()).build();
      case CONV_ID -> builder.setConvId(request.getConvId()).build();
      case GROUP_ID -> builder.setGroupId(request.getGroupId()).build();
      case TARGET_NOT_SET -> throw new ImException(ErrorCode.VALIDATION_FAILED, "message target is required");
    };
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
    metadata.put(GrpcMetadataKeys.CALLER, "im-message-service");
    return metadata;
  }
}
