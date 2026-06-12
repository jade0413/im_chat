package com.im.message.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.im.common.grpc.TenantContextServerInterceptor;
import com.im.common.tenant.TenantContext;
import com.im.proto.body.ConvInfo;
import com.im.proto.body.MsgSend;
import com.im.proto.common.ConvType;
import com.im.proto.common.MsgContent;
import com.im.proto.common.TextContent;
import com.im.proto.rpc.ConnCtx;
import com.im.proto.rpc.ConversationRpcGrpc;
import com.im.proto.rpc.ResolveConvReq;
import com.im.proto.rpc.ResolveConvResp;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GrpcConversationResolverTest {

  private Server server;
  private ManagedChannel channel;

  @AfterEach
  void tearDown() {
    if (channel != null) {
      channel.shutdownNow();
    }
    if (server != null) {
      server.shutdownNow();
    }
  }

  @Test
  void sendsTenantMetadataAndResolvesConversation() throws Exception {
    AtomicLong observedTenantId = new AtomicLong();
    ConversationRpcGrpc.ConversationRpcImplBase service = new ConversationRpcGrpc.ConversationRpcImplBase() {
      @Override
      public void resolveConv(ResolveConvReq request, StreamObserver<ResolveConvResp> responseObserver) {
        observedTenantId.set(TenantContext.requiredTenantId());
        responseObserver.onNext(ResolveConvResp.newBuilder()
            .setCode(0)
            .setConv(ConvInfo.newBuilder()
                .setConvId(501L)
                .setType(ConvType.C2C)
                .setPeerUserId(request.getToUserId()))
            .build());
        responseObserver.onCompleted();
      }
    };

    String serverName = InProcessServerBuilder.generateName() + UUID.randomUUID();
    server = InProcessServerBuilder.forName(serverName)
        .directExecutor()
        .addService(ServerInterceptors.intercept(service, new TenantContextServerInterceptor()))
        .build()
        .start();
    channel = InProcessChannelBuilder.forName(serverName)
        .directExecutor()
        .build();

    GrpcConversationResolver resolver = new GrpcConversationResolver(
        ConversationRpcGrpc.newBlockingStub(channel));
    TenantContext.runWithTenant(1L, () -> {
      ConvInfo conv = resolver.resolve(ctx(), request());
      assertThat(conv.getConvId()).isEqualTo(501L);
    });

    assertThat(observedTenantId.get()).isEqualTo(1L);
  }

  private ConnCtx ctx() {
    return ConnCtx.newBuilder()
        .setTenantId(1L)
        .setUserId(100L)
        .setTraceId("trace-1")
        .build();
  }

  private MsgSend request() {
    return MsgSend.newBuilder()
        .setClientMsgId("client-1")
        .setToUserId(200L)
        .setContent(MsgContent.newBuilder()
            .setText(TextContent.newBuilder().setText("hello")))
        .build();
  }
}
