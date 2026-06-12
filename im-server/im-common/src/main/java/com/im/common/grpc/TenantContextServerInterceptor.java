package com.im.common.grpc;

import com.im.common.tenant.TenantContext;
import com.im.common.trace.TraceContext;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.springframework.stereotype.Component;

@Component
public class TenantContextServerInterceptor implements ServerInterceptor {

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call,
      Metadata headers,
      ServerCallHandler<ReqT, RespT> next) {
    Long tenantId = parseTenantId(headers.get(GrpcMetadataKeys.TENANT_ID), call);
    if (INVALID_TENANT.equals(tenantId)) {
      return new ServerCall.Listener<>() {
      };
    }
    String traceId = headers.get(GrpcMetadataKeys.TRACE_ID);
    ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);
    if (tenantId == null && (traceId == null || traceId.isBlank())) {
      return delegate;
    }
    return new ContextAwareListener<>(delegate, tenantId, traceId);
  }

  private static final Long INVALID_TENANT = Long.MIN_VALUE;

  private Long parseTenantId(String rawTenantId, ServerCall<?, ?> call) {
    if (rawTenantId == null || rawTenantId.isBlank()) {
      return null;
    }
    try {
      long tenantId = Long.parseLong(rawTenantId);
      if (tenantId <= 0) {
        throw new NumberFormatException("tenant_id must be positive");
      }
      return tenantId;
    } catch (NumberFormatException ex) {
      call.close(Status.INVALID_ARGUMENT.withDescription("invalid tenant_id metadata"), new Metadata());
      return INVALID_TENANT;
    }
  }

  private static final class ContextAwareListener<ReqT>
      extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

    private final Long tenantId;
    private final String traceId;

    private ContextAwareListener(ServerCall.Listener<ReqT> delegate, Long tenantId, String traceId) {
      super(delegate);
      this.tenantId = tenantId;
      this.traceId = traceId;
    }

    @Override
    public void onMessage(ReqT message) {
      runInContext(() -> super.onMessage(message));
    }

    @Override
    public void onHalfClose() {
      runInContext(super::onHalfClose);
    }

    @Override
    public void onCancel() {
      runInContext(super::onCancel);
    }

    @Override
    public void onComplete() {
      runInContext(super::onComplete);
    }

    @Override
    public void onReady() {
      runInContext(super::onReady);
    }

    private void runInContext(Runnable action) {
      Runnable traced = traceId == null || traceId.isBlank()
          ? action
          : () -> TraceContext.runWithTraceId(traceId, action);
      if (tenantId == null) {
        traced.run();
        return;
      }
      TenantContext.runWithTenant(tenantId, traced);
    }
  }
}
