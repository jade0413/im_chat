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
import com.im.proto.body.ReadNotify;
import com.im.proto.body.ReadReport;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractImServerMvpSmokeTest {

  protected static final long TENANT_ID = 1L;
  protected static final int GRPC_PORT = freeTcpPort();

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
    MsgSendAck firstAck = sendTextToUser(uplink, userA, userB,
        "device-a", "hello from alice", 1L);
    assertThat(firstAck.getCode()).isZero();
    assertThat(firstAck.getConvId()).isPositive();
    assertThat(firstAck.getServerMsgId()).isPositive();
    assertThat(firstAck.getSeq()).isEqualTo(1L);

    SyncResp syncResp = sync(uplink, userB, firstAck.getConvId(), 0L, "device-b", 2L);
    assertThat(syncResp.getDeltasList()).hasSize(1);
    SyncResp.ConvDelta delta = syncResp.getDeltas(0);
    assertThat(delta.getConv().getConvId()).isEqualTo(firstAck.getConvId());
    assertThat(delta.getServerMaxSeq()).isEqualTo(1L);
    assertThat(delta.getMsgsList()).hasSize(1);
    MsgPush firstSynced = delta.getMsgs(0);
    assertThat(firstSynced.getSeq()).isEqualTo(1L);
    assertThat(firstSynced.getServerMsgId()).isEqualTo(firstAck.getServerMsgId());
    assertThat(firstSynced.getContent().getText().getText()).isEqualTo("hello from alice");

    ReadNotify readNotify = reportRead(uplink, userB, firstAck.getConvId(), 1L, "device-b", 3L);
    assertThat(readNotify.getConvId()).isEqualTo(firstAck.getConvId());
    assertThat(readNotify.getReaderUserId()).isEqualTo(userB);
    assertThat(readNotify.getReadSeq()).isEqualTo(1L);

    MsgSendAck secondAck = sendTextToConversation(uplink, userB, firstAck.getConvId(),
        "device-b", "reply from bob", 4L);
    assertThat(secondAck.getCode()).isZero();
    assertThat(secondAck.getConvId()).isEqualTo(firstAck.getConvId());
    assertThat(secondAck.getServerMsgId()).isPositive();
    assertThat(secondAck.getSeq()).isEqualTo(2L);

    disconnectGatewayChannel();
    GatewayAuthGrpc.GatewayAuthBlockingStub reconnectedAuth = GatewayAuthGrpc.newBlockingStub(channel());
    assertThat(verifyToken(reconnectedAuth, tokensA.accessToken(), "device-a-reconnect")).isEqualTo(userA);
    UplinkGrpc.UplinkBlockingStub reconnectedUplink = UplinkGrpc.newBlockingStub(channel());
    SyncResp reconnectSync = sync(reconnectedUplink, userA, firstAck.getConvId(),
        1L, "device-a-reconnect", 5L);
    assertThat(reconnectSync.getDeltasList()).hasSize(1);
    SyncResp.ConvDelta reconnectDelta = reconnectSync.getDeltas(0);
    assertThat(reconnectDelta.getServerMaxSeq()).isEqualTo(2L);
    assertThat(reconnectDelta.getMsgsList()).hasSize(1);
    MsgPush secondSynced = reconnectDelta.getMsgs(0);
    assertThat(secondSynced.getSeq()).isEqualTo(2L);
    assertThat(secondSynced.getServerMsgId()).isEqualTo(secondAck.getServerMsgId());
    assertThat(secondSynced.getContent().getText().getText()).isEqualTo("reply from bob");

    JsonNode history = history(tokensB.accessToken(), firstAck.getConvId());
    assertThat(history.at("/data/convId").asLong()).isEqualTo(firstAck.getConvId());
    assertThat(history.at("/data/readSeq").asLong()).isEqualTo(1L);
    assertThat(history.at("/data/messages")).hasSize(2);
    assertThat(history.at("/data/messages/0/seq").asLong()).isEqualTo(2L);
    assertThat(history.at("/data/messages/0/serverMsgId").asLong()).isEqualTo(secondAck.getServerMsgId());
    assertThat(history.at("/data/messages/0/text").asText()).isEqualTo("reply from bob");
    assertThat(history.at("/data/messages/1/seq").asLong()).isEqualTo(1L);
    assertThat(history.at("/data/messages/1/serverMsgId").asLong()).isEqualTo(firstAck.getServerMsgId());
    assertThat(history.at("/data/messages/1/text").asText()).isEqualTo("hello from alice");

    assertDatabaseState(userA, userB, firstAck, secondAck);

    JsonNode group = createGroup(tokensA.accessToken(), "e2e group " + suffix, userB);
    long groupId = group.at("/data/groupId").asLong();
    long groupConvId = group.at("/data/convId").asLong();
    assertThat(groupId).isPositive();
    assertThat(groupConvId).isPositive();
    assertThat(group.at("/data/memberCount").asInt()).isEqualTo(2);

    MsgSendAck groupAck = sendTextToGroup(reconnectedUplink, userA, groupId,
        "device-a-reconnect", "hello group", 6L);
    assertThat(groupAck.getCode()).isZero();
    assertThat(groupAck.getConvId()).isEqualTo(groupConvId);
    assertThat(groupAck.getSeq()).isEqualTo(2L);

    SyncResp groupSync = sync(reconnectedUplink, userB, groupConvId, 0L,
        "device-b", 7L);
    assertThat(groupSync.getDeltasList()).hasSize(1);
    SyncResp.ConvDelta groupDelta = groupSync.getDeltas(0);
    assertThat(groupDelta.getConv().getType()).isEqualTo(ConvType.GROUP);
    assertThat(groupDelta.getConv().getGroupId()).isEqualTo(groupId);
    assertThat(groupDelta.getConv().getTitle()).isEqualTo("e2e group " + suffix);
    assertThat(groupDelta.getServerMaxSeq()).isEqualTo(2L);
    assertThat(groupDelta.getMsgsList()).hasSize(2);
    assertThat(groupDelta.getMsgs(0).getContent().getNotification().getEventType())
        .isEqualTo("group.created");
    assertThat(groupDelta.getMsgs(1).getContent().getText().getText()).isEqualTo("hello group");
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

  private MsgSendAck sendTextToUser(UplinkGrpc.UplinkBlockingStub uplink,
      long senderId,
      long toUserId,
      String deviceId,
      String text,
      long reqId)
      throws Exception {
    MsgSend request = MsgSend.newBuilder()
        .setClientMsgId("client-" + UUID.randomUUID())
        .setToUserId(toUserId)
        .setContent(MsgContent.newBuilder()
            .setText(TextContent.newBuilder().setText(text)))
        .build();
    return sendText(uplink, senderId, deviceId, request, reqId);
  }

  private MsgSendAck sendTextToConversation(UplinkGrpc.UplinkBlockingStub uplink,
      long senderId,
      long conversationId,
      String deviceId,
      String text,
      long reqId)
      throws Exception {
    MsgSend request = MsgSend.newBuilder()
        .setClientMsgId("client-" + UUID.randomUUID())
        .setConvId(conversationId)
        .setContent(MsgContent.newBuilder()
            .setText(TextContent.newBuilder().setText(text)))
        .build();
    return sendText(uplink, senderId, deviceId, request, reqId);
  }

  private MsgSendAck sendTextToGroup(UplinkGrpc.UplinkBlockingStub uplink,
      long senderId,
      long groupId,
      String deviceId,
      String text,
      long reqId)
      throws Exception {
    MsgSend request = MsgSend.newBuilder()
        .setClientMsgId("client-" + UUID.randomUUID())
        .setGroupId(groupId)
        .setContent(MsgContent.newBuilder()
            .setText(TextContent.newBuilder().setText(text)))
        .build();
    return sendText(uplink, senderId, deviceId, request, reqId);
  }

  private MsgSendAck sendText(UplinkGrpc.UplinkBlockingStub uplink,
      long senderId,
      String deviceId,
      MsgSend request,
      long reqId)
      throws Exception {
    UplinkResp response = uplink.dispatch(UplinkReq.newBuilder()
        .setReqId(reqId)
        .setCtx(ctx(senderId, deviceId))
        .setCmd(Cmd.MSG_SEND_VALUE)
        .setBody(request.toByteString())
        .build());

    assertThat(response.getCmd()).isEqualTo(Cmd.MSG_SEND_ACK_VALUE);
    return MsgSendAck.parseFrom(response.getBody());
  }

  private SyncResp sync(UplinkGrpc.UplinkBlockingStub uplink,
      long userId,
      long convId,
      long localMaxSeq,
      String deviceId,
      long reqId)
      throws Exception {
    SyncReq request = SyncReq.newBuilder()
        .addConvVersions(SyncReq.ConvVersion.newBuilder()
            .setConvId(convId)
            .setLocalMaxSeq(localMaxSeq))
        .build();

    UplinkResp response = uplink.dispatch(UplinkReq.newBuilder()
        .setReqId(reqId)
        .setCtx(ctx(userId, deviceId))
        .setCmd(Cmd.SYNC_REQ_VALUE)
        .setBody(request.toByteString())
        .build());

    assertThat(response.getCmd()).isEqualTo(Cmd.SYNC_RESP_VALUE);
    return SyncResp.parseFrom(response.getBody());
  }

  private ReadNotify reportRead(UplinkGrpc.UplinkBlockingStub uplink,
      long userId,
      long convId,
      long readSeq,
      String deviceId,
      long reqId)
      throws Exception {
    ReadReport request = ReadReport.newBuilder()
        .setConvId(convId)
        .setReadSeq(readSeq)
        .build();
    UplinkResp response = uplink.dispatch(UplinkReq.newBuilder()
        .setReqId(reqId)
        .setCtx(ctx(userId, deviceId))
        .setCmd(Cmd.READ_REPORT_VALUE)
        .setBody(request.toByteString())
        .build());

    assertThat(response.getCmd()).isEqualTo(Cmd.READ_NOTIFY_VALUE);
    return ReadNotify.parseFrom(response.getBody());
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

  private JsonNode createGroup(String accessToken, String name, long memberUserId) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/groups")
            .header(TenantContextFilter.TENANT_HEADER, TENANT_ID)
            .header("Authorization", "Bearer " + accessToken)
            .contentType("application/json")
            .content("""
                {"name":"%s","memberUserIds":[%d]}
                """.formatted(name, memberUserId)))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsByteArray());
  }

  private void assertDatabaseState(long userA, long userB, MsgSendAck firstAck, MsgSendAck secondAck)
      throws Exception {
    TenantContext.callWithTenant(TENANT_ID, () -> {
      ConversationEntity conversation = conversationMapper.selectById(firstAck.getConvId());
      assertThat(conversation).isNotNull();
      assertThat(conversation.getType()).isEqualTo(ConvType.C2C.getNumber());
      assertThat(conversation.getMaxSeq()).isEqualTo(2L);
      assertThat(conversation.getLastMsgAbstract()).isEqualTo("reply from bob");

      List<ConversationMemberEntity> members = conversationMemberMapper.selectList(
          Wrappers.<ConversationMemberEntity>lambdaQuery()
              .eq(ConversationMemberEntity::getConvId, firstAck.getConvId()));
      assertThat(members).extracting(ConversationMemberEntity::getUserId)
          .containsExactlyInAnyOrder(userA, userB);
      assertThat(members.stream()
          .filter(member -> member.getUserId().equals(userB))
          .findFirst()
          .orElseThrow()
          .getReadSeq()).isEqualTo(1L);

      MessageEntity firstMessage = messageMapper.selectById(firstAck.getServerMsgId());
      assertThat(firstMessage).isNotNull();
      assertThat(firstMessage.getConversationId()).isEqualTo(firstAck.getConvId());
      assertThat(firstMessage.getSeq()).isEqualTo(1L);
      assertThat(firstMessage.getSenderId()).isEqualTo(userA);
      assertThat(firstMessage.getAbstractText()).isEqualTo("hello from alice");

      MessageEntity secondMessage = messageMapper.selectById(secondAck.getServerMsgId());
      assertThat(secondMessage).isNotNull();
      assertThat(secondMessage.getConversationId()).isEqualTo(firstAck.getConvId());
      assertThat(secondMessage.getSeq()).isEqualTo(2L);
      assertThat(secondMessage.getSenderId()).isEqualTo(userB);
      assertThat(secondMessage.getAbstractText()).isEqualTo("reply from bob");

      List<OutboxEntity> outboxes = outboxMapper.selectList(Wrappers.<OutboxEntity>lambdaQuery()
          .eq(OutboxEntity::getEventType, MsgSavedEventFactory.EVENT_TYPE));
      assertThat(outboxes).hasSize(2);
      List<MsgSavedEvent> events = outboxes.stream()
          .map(this::parseEvent)
          .toList();
      assertThat(events).extracting(MsgSavedEvent::getServerMsgId)
          .containsExactlyInAnyOrder(firstAck.getServerMsgId(), secondAck.getServerMsgId());
      assertThat(events).extracting(event -> event.getPushReady().getContent().getText().getText())
          .containsExactlyInAnyOrder("hello from alice", "reply from bob");
      return null;
    });
  }

  private MsgSavedEvent parseEvent(OutboxEntity event) {
    try {
      return MsgSavedEvent.parseFrom(event.getPayload());
    } catch (Exception ex) {
      throw new IllegalStateException("invalid msg.saved payload", ex);
    }
  }

  private void disconnectGatewayChannel() throws InterruptedException {
    if (channel == null) {
      return;
    }
    channel.shutdownNow();
    assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    channel = null;
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
