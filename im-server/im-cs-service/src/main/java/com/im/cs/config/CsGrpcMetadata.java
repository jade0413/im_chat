package com.im.cs.config;

import com.im.common.grpc.GrpcMetadataKeys;
import com.im.common.tenant.TenantContext;
import com.im.common.trace.TraceContext;
import com.im.proto.rpc.ConversationRpcGrpc;
import com.im.proto.rpc.UserRpcGrpc;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

/**
 * CS 模块内部 gRPC 调用的 metadata 透传工具。
 *
 * <p>CS REST 请求入口已经由 TenantContextFilter 写入 TenantContext；
 * 这里把 tenant/trace 继续传给 user/conversation gRPC 服务，否则下游 MyBatis
 * 租户拦截器拿不到 tenant_id，会抛 TenantRequiredException。
 */
public final class CsGrpcMetadata {

  private static final String CALLER = "im-cs-service";

  private CsGrpcMetadata() {
  }

  public static UserRpcGrpc.UserRpcBlockingStub withMetadata(
      UserRpcGrpc.UserRpcBlockingStub stub) {
    return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata()));
  }

  public static ConversationRpcGrpc.ConversationRpcBlockingStub withMetadata(
      ConversationRpcGrpc.ConversationRpcBlockingStub stub) {
    return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata()));
  }

  private static Metadata metadata() {
    Metadata metadata = new Metadata();
    metadata.put(GrpcMetadataKeys.TENANT_ID, String.valueOf(TenantContext.requiredTenantId()));
    TraceContext.currentTraceId()
        .ifPresent(traceId -> metadata.put(GrpcMetadataKeys.TRACE_ID, traceId));
    metadata.put(GrpcMetadataKeys.CALLER, CALLER);
    return metadata;
  }
}
