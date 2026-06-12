package com.im.bootstrap.grpc;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UplinkGrpcServiceTest {

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
  void dispatchesToRegisteredHandler() throws Exception {
    UplinkGrpc.UplinkBlockingStub stub = startWithHandlers(List.of(new CmdHandler() {
      @Override
      public int cmd() {
        return Cmd.MSG_SEND_VALUE;
      }

      @Override
      public int responseCmd() {
        return Cmd.MSG_SEND_ACK_VALUE;
      }

      @Override
      public byte[] handle(ConnCtx ctx, byte[] body) {
        assertThat(TenantContext.requiredTenantId()).isEqualTo(ctx.getTenantId());
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("send");
        return "ack".getBytes(StandardCharsets.UTF_8);
      }
    }));

    UplinkResp response = stub.dispatch(request(Cmd.MSG_SEND_VALUE, 1001L, "send"));

    assertThat(response.getCmd()).isEqualTo(Cmd.MSG_SEND_ACK_VALUE);
    assertThat(response.getBody().toStringUtf8()).isEqualTo("ack");
  }

  @Test
  void returnsErrorForUnknownCmd() throws Exception {
    UplinkGrpc.UplinkBlockingStub stub = startWithHandlers(List.of());

    UplinkResp response = stub.dispatch(request(777, 1002L, ""));

    assertError(response, ErrorCode.VALIDATION_FAILED, 1002L);
  }

  @Test
  void returnsStructuredErrorForImException() throws Exception {
    UplinkGrpc.UplinkBlockingStub stub = startWithHandlers(List.of(new ThrowingHandler(
        Cmd.MSG_SEND_VALUE,
        new ImException(ErrorCode.TOKEN_INVALID, "bad token"))));

    UplinkResp response = stub.dispatch(request(Cmd.MSG_SEND_VALUE, 1003L, ""));

    assertError(response, ErrorCode.TOKEN_INVALID, 1003L);
  }

  @Test
  void hidesUnexpectedHandlerException() throws Exception {
    UplinkGrpc.UplinkBlockingStub stub = startWithHandlers(List.of(new ThrowingHandler(
        Cmd.MSG_SEND_VALUE,
        new IllegalStateException("boom"))));

    UplinkResp response = stub.dispatch(request(Cmd.MSG_SEND_VALUE, 1004L, ""));

    assertError(response, ErrorCode.INTERNAL_ERROR, 1004L);
  }

  @Test
  void returnsErrorForMissingContext() throws Exception {
    UplinkGrpc.UplinkBlockingStub stub = startWithHandlers(List.of());

    UplinkResp response = stub.dispatch(UplinkReq.newBuilder()
        .setCmd(Cmd.MSG_SEND_VALUE)
        .setReqId(1005L)
        .build());

    assertError(response, ErrorCode.VALIDATION_FAILED, 1005L);
  }

  private UplinkGrpc.UplinkBlockingStub startWithHandlers(List<CmdHandler> handlers) throws Exception {
    String serverName = InProcessServerBuilder.generateName() + UUID.randomUUID();
    UplinkGrpcService service = new UplinkGrpcService(new CmdHandlerRegistry(handlers));
    server = InProcessServerBuilder.forName(serverName)
        .directExecutor()
        .addService(service)
        .build()
        .start();
    channel = InProcessChannelBuilder.forName(serverName)
        .directExecutor()
        .build();
    return UplinkGrpc.newBlockingStub(channel);
  }

  private UplinkReq request(int cmd, long reqId, String body) {
    return UplinkReq.newBuilder()
        .setCtx(ConnCtx.newBuilder()
            .setTenantId(1L)
            .setUserId(101L)
            .setPlatform(1)
            .setDeviceId("device-1")
            .setConnId("conn-1")
            .setGwInstance("gw-1")
            .setTraceId("trace-1")
            .build())
        .setCmd(cmd)
        .setReqId(reqId)
        .setBody(ByteString.copyFromUtf8(body))
        .build();
  }

  private void assertError(UplinkResp response, ErrorCode errorCode, long reqId) throws Exception {
    assertThat(response.getCmd()).isEqualTo(Cmd.ERROR_VALUE);
    ErrorBody errorBody = ErrorBody.parseFrom(response.getBody());
    assertThat(errorBody.getCode()).isEqualTo(errorCode.code());
    assertThat(errorBody.getReqId()).isEqualTo(reqId);
  }

  private record ThrowingHandler(int cmd, RuntimeException exception) implements CmdHandler {

    @Override
    public byte[] handle(ConnCtx ctx, byte[] body) {
      throw exception;
    }
  }
}
