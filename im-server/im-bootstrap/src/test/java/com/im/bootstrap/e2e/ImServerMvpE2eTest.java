package com.im.bootstrap.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.outbox.dao.entity.OutboxEntity;
import com.im.common.outbox.dao.mapper.CommonOutboxMapper;
import com.im.common.tenant.TenantContext;
import com.im.common.test.IntegrationTestSupport;
import com.im.common.web.TenantContextFilter;
import com.im.conversation.dao.entity.ConversationEntity;
import com.im.conversation.dao.entity.ConversationMemberEntity;
import com.im.conversation.dao.mapper.ConversationMapper;
import com.im.conversation.dao.mapper.ConversationMemberMapper;
import com.im.message.dao.entity.MessageEntity;
import com.im.message.dao.mapper.MessageMapper;
import com.im.message.service.MsgSavedEventFactory;
import com.im.proto.body.MsgPush;
import com.im.proto.body.MsgSend;
import com.im.proto.body.MsgSendAck;
import com.im.proto.body.SyncReq;
import com.im.proto.body.SyncResp;
import com.im.proto.common.ConvType;
import com.im.proto.common.MsgContent;
import com.im.proto.common.TextContent;
import com.im.proto.events.MsgSavedEvent;
import com.im.proto.rpc.ConnCtx;
import com.im.proto.rpc.GatewayAuthGrpc;
import com.im.proto.rpc.UplinkGrpc;
import com.im.proto.rpc.UplinkReq;
import com.im.proto.rpc.UplinkResp;
import com.im.proto.rpc.VerifyTokenReq;
import com.im.proto.rpc.VerifyTokenResp;
import com.im.proto.ws.Cmd;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ImServerMvpE2eTest extends IntegrationTestSupport {

  private static final long TENANT_ID = 1L;
  private static final int GRPC_PORT = freeTcpPort();

  @Container
  static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
      .withExposedPorts(6379);

  @DynamicPropertySource
  static void registerE2eProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("im.grpc.port", () -> GRPC_PORT);
    registry.add("im.rpc.conversation.host", () -> "localhost");
    registry.add("im.rpc.conversation.port", () -> GRPC_PORT);
    registry.add("im.outbox.enabled", () -> false);
  }

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ConversationMapper conversationMapper;

  @Autowired
  private ConversationMemberMapper conversationMemberMapper;

  @Autowired
  private MessageMapper messageMapper;

  @Autowired
  private CommonOutboxMapper outboxMapper;

  private ManagedChannel channel;

  @AfterEach
  void tearDown() throws InterruptedException {
    if (channel != null) {
      channel.shutdownNow();
      assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void mvpFlowThroughRestGatewayAuthUplinkSyncAndHistory() throws Exception {
    String suffix = Long.toString(System.nanoTime());
    String accountA = "e2e_a_" + suffix;
    String accountB = "e2e_b_" + suffix;
    register(accountA, "Alice");
    register(accountB, "Bob");

    TokenPair tokensA = login(accountA);
    TokenPair tokensB = login(accountB);

    GatewayAuthGrpc.GatewayAuthBlockingStub gatewayAuth = GatewayAuthGrpc.newBlockingStub(channel());
    long userA = verifyToken(gatewayAuth, tokensA.accessToken(), "device-a");
    long userB = verifyToken(gatewayAuth, tokensB.accessToken(), "device-b");
    assertThat(userA).isPositive();
    assertThat(userB).isPositive().isNotEqualTo(userA);

    UplinkGrpc.UplinkBlockingStub uplink = UplinkGrpc.newBlockingStub(channel());
    MsgSendAck ack = sendTextMessage(uplink, userA, userB);
    assertThat(ack.getCode()).isZero();
    assertThat(ack.getConvId()).isPositive();
    assertThat(ack.getServerMsgId()).isPositive();
    assertThat(ack.getSeq()).isEqualTo(1L);

    SyncResp syncResp = syncFromBeginning(uplink, userB, ack.getConvId());
    assertThat(syncResp.getDeltasList()).hasSize(1);
    SyncResp.ConvDelta delta = syncResp.getDeltas(0);
    assertThat(delta.getConv().getConvId()).isEqualTo(ack.getConvId());
    assertThat(delta.getServerMaxSeq()).isEqualTo(1L);
    assertThat(delta.getMsgsList()).hasSize(1);
    MsgPush synced = delta.getMsgs(0);
    assertThat(synced.getSeq()).isEqualTo(1L);
    assertThat(synced.getServerMsgId()).isEqualTo(ack.getServerMsgId());
    assertThat(synced.getContent().getText().getText()).isEqualTo("hello from e2e");

    JsonNode history = history(tokensB.accessToken(), ack.getConvId());
    assertThat(history.at("/data/convId").asLong()).isEqualTo(ack.getConvId());
    assertThat(history.at("/data/messages")).hasSize(1);
    assertThat(history.at("/data/messages/0/seq").asLong()).isEqualTo(1L);
    assertThat(history.at("/data/messages/0/serverMsgId").asLong()).isEqualTo(ack.getServerMsgId());
    assertThat(history.at("/data/messages/0/text").asText()).isEqualTo("hello from e2e");

    assertDatabaseState(userA, userB, ack);
  }

  private void register(String account, String nickname) throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
            .header(TenantContextFilter.TENANT_HEADER, TENANT_ID)
            .contentType("application/json")
            .content("""
                {"account":"%s","password":"password123","nickname":"%s","platform":1}
                """.formatted(account, nickname)))
        .andExpect(status().isOk());
  }

  private TokenPair login(String account) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
            .header(TenantContextFilter.TENANT_HEADER, TENANT_ID)
            .contentType("application/json")
            .content("""
                {"account":"%s","password":"password123","platform":1}
                """.formatted(account)))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    return new TokenPair(
        json.at("/data/accessToken").asText(),
        json.at("/data/refreshToken").asText());
  }

  private long verifyToken(GatewayAuthGrpc.GatewayAuthBlockingStub gatewayAuth,
      String accessToken,
      String deviceId) {
    VerifyTokenResp response = gatewayAuth.verifyToken(VerifyTokenReq.newBuilder()
        .setToken(accessToken)
        .setTenantId(TENANT_ID)
        .setDeviceId(deviceId)
        .setPlatform(1)
        .setGwInstance("e2e-gw")
        .build());
    assertThat(response.getCode()).isZero();
    assertThat(response.getHeartbeatIntervalSec()).isPositive();
    return response.getUserId();
  }

  private MsgSendAck sendTextMessage(UplinkGrpc.UplinkBlockingStub uplink, long userA, long userB)
      throws Exception {
    MsgSend request = MsgSend.newBuilder()
        .setClientMsgId("client-" + UUID.randomUUID())
        .setToUserId(userB)
        .setContent(MsgContent.newBuilder()
            .setText(TextContent.newBuilder().setText("hello from e2e")))
        .build();

    UplinkResp response = uplink.dispatch(UplinkReq.newBuilder()
        .setReqId(1L)
        .setCtx(ctx(userA, "device-a"))
        .setCmd(Cmd.MSG_SEND_VALUE)
        .setBody(request.toByteString())
        .build());

    assertThat(response.getCmd()).isEqualTo(Cmd.MSG_SEND_ACK_VALUE);
    return MsgSendAck.parseFrom(response.getBody());
  }

  private SyncResp syncFromBeginning(UplinkGrpc.UplinkBlockingStub uplink, long userB, long convId)
      throws Exception {
    SyncReq request = SyncReq.newBuilder()
        .addConvVersions(SyncReq.ConvVersion.newBuilder()
            .setConvId(convId)
            .setLocalMaxSeq(0L))
        .build();

    UplinkResp response = uplink.dispatch(UplinkReq.newBuilder()
        .setReqId(2L)
        .setCtx(ctx(userB, "device-b"))
        .setCmd(Cmd.SYNC_REQ_VALUE)
        .setBody(request.toByteString())
        .build());

    assertThat(response.getCmd()).isEqualTo(Cmd.SYNC_RESP_VALUE);
    return SyncResp.parseFrom(response.getBody());
  }

  private JsonNode history(String accessToken, long convId) throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/convs/{convId}/messages", convId)
            .header(TenantContextFilter.TENANT_HEADER, TENANT_ID)
            .header("Authorization", "Bearer " + accessToken)
            .queryParam("limit", "20"))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsByteArray());
  }

  private void assertDatabaseState(long userA, long userB, MsgSendAck ack) throws Exception {
    TenantContext.callWithTenant(TENANT_ID, () -> {
      ConversationEntity conversation = conversationMapper.selectById(ack.getConvId());
      assertThat(conversation).isNotNull();
      assertThat(conversation.getType()).isEqualTo(ConvType.C2C.getNumber());
      assertThat(conversation.getMaxSeq()).isEqualTo(1L);
      assertThat(conversation.getLastMsgAbstract()).isEqualTo("hello from e2e");

      List<ConversationMemberEntity> members = conversationMemberMapper.selectList(
          Wrappers.<ConversationMemberEntity>lambdaQuery()
              .eq(ConversationMemberEntity::getConvId, ack.getConvId()));
      assertThat(members).extracting(ConversationMemberEntity::getUserId)
          .containsExactlyInAnyOrder(userA, userB);

      MessageEntity message = messageMapper.selectById(ack.getServerMsgId());
      assertThat(message).isNotNull();
      assertThat(message.getConversationId()).isEqualTo(ack.getConvId());
      assertThat(message.getSeq()).isEqualTo(1L);
      assertThat(message.getSenderId()).isEqualTo(userA);
      assertThat(message.getAbstractText()).isEqualTo("hello from e2e");

      List<OutboxEntity> outboxes = outboxMapper.selectList(Wrappers.<OutboxEntity>lambdaQuery()
          .eq(OutboxEntity::getEventType, MsgSavedEventFactory.EVENT_TYPE));
      assertThat(outboxes).hasSize(1);
      MsgSavedEvent event = MsgSavedEvent.parseFrom(outboxes.getFirst().getPayload());
      assertThat(event.getTenantId()).isEqualTo(TENANT_ID);
      assertThat(event.getConvId()).isEqualTo(ack.getConvId());
      assertThat(event.getServerMsgId()).isEqualTo(ack.getServerMsgId());
      assertThat(event.getPushReady().getContent().getText().getText()).isEqualTo("hello from e2e");
      return null;
    });
  }

  private ConnCtx ctx(long userId, String deviceId) {
    return ConnCtx.newBuilder()
        .setTenantId(TENANT_ID)
        .setUserId(userId)
        .setPlatform(1)
        .setDeviceId(deviceId)
        .setConnId("conn-" + deviceId)
        .setGwInstance("e2e-gw")
        .setTraceId("trace-e2e")
        .build();
  }

  private ManagedChannel channel() {
    if (channel == null) {
      channel = ManagedChannelBuilder.forAddress("localhost", GRPC_PORT)
          .usePlaintext()
          .build();
    }
    return channel;
  }

  private static int freeTcpPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException ex) {
      throw new IllegalStateException("failed to allocate grpc port", ex);
    }
  }

  private record TokenPair(String accessToken, String refreshToken) {
  }
}
