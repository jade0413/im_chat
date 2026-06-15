package com.im.push.grpcapi;

import com.im.common.tenant.TenantContext;
import com.im.proto.rpc.ConnCtx;
import com.im.proto.rpc.ConnEventGrpc;
import com.im.proto.rpc.Empty;
import com.im.proto.rpc.PushAckReq;
import com.im.push.service.PushDispatchService;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

@Service
public class ConnEventGrpcService extends ConnEventGrpc.ConnEventImplBase {

  private final PushDispatchService pushDispatchService;

  public ConnEventGrpcService(PushDispatchService pushDispatchService) {
    this.pushDispatchService = pushDispatchService;
  }

  @Override
  public void onConnected(ConnCtx request, StreamObserver<Empty> responseObserver) {
    TenantContext.runWithTenant(request.getTenantId(), () -> pushDispatchService.onConnected(request));
    complete(responseObserver);
  }

  @Override
  public void refreshRoute(ConnCtx request, StreamObserver<Empty> responseObserver) {
    TenantContext.runWithTenant(request.getTenantId(), () -> pushDispatchService.refreshRoute(request));
    complete(responseObserver);
  }

  @Override
  public void onDisconnected(ConnCtx request, StreamObserver<Empty> responseObserver) {
    TenantContext.runWithTenant(request.getTenantId(), () -> pushDispatchService.onDisconnected(request));
    complete(responseObserver);
  }

  @Override
  public void onPushAcked(PushAckReq request, StreamObserver<Empty> responseObserver) {
    complete(responseObserver);
  }

  private void complete(StreamObserver<Empty> responseObserver) {
    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }
}
