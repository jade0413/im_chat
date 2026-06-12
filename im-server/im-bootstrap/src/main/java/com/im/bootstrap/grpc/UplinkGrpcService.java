package com.im.bootstrap.grpc;

import com.google.protobuf.ByteString;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.common.uplink.CmdHandler;
import com.im.common.uplink.CmdHandlerRegistry;
import com.im.proto.body.ErrorBody;
import com.im.proto.rpc.ConnCtx;
import com.im.proto.rpc.UplinkGrpc;
import com.im.proto.rpc.UplinkReq;
import com.im.proto.rpc.UplinkResp;
import com.im.proto.ws.Cmd;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UplinkGrpcService extends UplinkGrpc.UplinkImplBase {

  private static final Logger log = LoggerFactory.getLogger(UplinkGrpcService.class);

  private final CmdHandlerRegistry cmdHandlerRegistry;

  public UplinkGrpcService(CmdHandlerRegistry cmdHandlerRegistry) {
    this.cmdHandlerRegistry = cmdHandlerRegistry;
  }

  @Override
  public void dispatch(UplinkReq request, StreamObserver<UplinkResp> responseObserver) {
    UplinkResp response;
    try {
      response = dispatchInternal(request);
    } catch (ImException ex) {
      response = errorResponse(request.getReqId(), ex.errorCode(), ex.getMessage());
    } catch (Exception ex) {
      ConnCtx ctx = request.hasCtx() ? request.getCtx() : ConnCtx.getDefaultInstance();
      log.error("uplink dispatch failed, tenant_id={}, user_id={}, cmd={}, req_id={}, trace_id={}",
          ctx.getTenantId(), ctx.getUserId(), request.getCmd(), request.getReqId(), ctx.getTraceId(), ex);
      response = errorResponse(request.getReqId(), ErrorCode.INTERNAL_ERROR,
          ErrorCode.INTERNAL_ERROR.defaultMessage());
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  private UplinkResp dispatchInternal(UplinkReq request) throws Exception {
    if (!request.hasCtx() || request.getCtx().getTenantId() <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "valid connection context is required");
    }
    CmdHandler handler = cmdHandlerRegistry.find(request.getCmd())
        .orElseThrow(() -> new ImException(ErrorCode.VALIDATION_FAILED,
            "unknown cmd: " + request.getCmd()));
    ConnCtx ctx = request.getCtx();
    byte[] responseBody = TenantContext.callWithTenant(ctx.getTenantId(),
        () -> handler.handle(ctx, request.getBody().toByteArray()));
    return UplinkResp.newBuilder()
        .setCmd(handler.responseCmd())
        .setBody(ByteString.copyFrom(responseBody == null ? new byte[0] : responseBody))
        .build();
  }

  private UplinkResp errorResponse(long reqId, ErrorCode errorCode, String message) {
    ErrorBody errorBody = ErrorBody.newBuilder()
        .setCode(errorCode.code())
        .setMessage(message == null || message.isBlank() ? errorCode.defaultMessage() : message)
        .setReqId(reqId)
        .build();
    return UplinkResp.newBuilder()
        .setCmd(Cmd.ERROR_VALUE)
        .setBody(errorBody.toByteString())
        .build();
  }
}
