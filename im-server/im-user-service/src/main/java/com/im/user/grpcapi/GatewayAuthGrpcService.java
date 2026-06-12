package com.im.user.grpcapi;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.proto.rpc.GatewayAuthGrpc;
import com.im.proto.rpc.VerifyTokenReq;
import com.im.proto.rpc.VerifyTokenResp;
import com.im.user.service.TokenVerifier;
import com.im.user.service.VerifyTokenResult;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

@Service
public class GatewayAuthGrpcService extends GatewayAuthGrpc.GatewayAuthImplBase {

  private final TokenVerifier tokenVerifier;

  public GatewayAuthGrpcService(TokenVerifier tokenVerifier) {
    this.tokenVerifier = tokenVerifier;
  }

  @Override
  public void verifyToken(VerifyTokenReq request, StreamObserver<VerifyTokenResp> responseObserver) {
    VerifyTokenResp response;
    try {
      VerifyTokenResult result = tokenVerifier.verify(
          request.getToken(), request.getTenantId(), request.getPlatform());
      response = VerifyTokenResp.newBuilder()
          .setCode(ErrorCode.OK.code())
          .setMessage(ErrorCode.OK.defaultMessage())
          .setUserId(result.userId())
          .setKickOld(false)
          .setHeartbeatIntervalSec(result.heartbeatIntervalSec())
          .build();
    } catch (ImException ex) {
      response = errorResponse(ex.errorCode(), ex.getMessage());
    } catch (Exception ex) {
      response = errorResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  private VerifyTokenResp errorResponse(ErrorCode errorCode, String message) {
    return VerifyTokenResp.newBuilder()
        .setCode(errorCode.code())
        .setMessage(message == null || message.isBlank() ? errorCode.defaultMessage() : message)
        .setKickOld(false)
        .setHeartbeatIntervalSec(0)
        .build();
  }
}
