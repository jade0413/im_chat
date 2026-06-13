package com.im.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.im.common.id.SnowflakeIdGenerator;
import com.im.message.dao.entity.MessageEntity;
import com.im.message.dao.entity.OutboxEntity;
import com.im.proto.body.ConvInfo;
import com.im.proto.body.MsgPush;
import com.im.proto.body.MsgSend;
import com.im.proto.common.ConvType;
import com.im.proto.common.ImageContent;
import com.im.proto.common.MsgContent;
import com.im.proto.common.RevokeReason;
import com.im.proto.common.TextContent;
import com.im.proto.events.MsgSavedEvent;
import com.im.proto.rpc.ConnCtx;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageAssemblerTest {

  @Mock
  private SnowflakeIdGenerator idGenerator;

  @Test
  void assemblesMessagePushAndOutbox() throws Exception {
    when(idGenerator.nextId()).thenReturn(9001L);
    MessageAssembler assembler = new MessageAssembler(
        idGenerator,
        Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneOffset.UTC));

    MessageEntity message = assembler.newTextMessage(ctx(), msgSend("client-1", "hello"),
        conv(), 7L);

    assertThat(message.getId()).isEqualTo(9001L);
    assertThat(message.getConversationId()).isEqualTo(501L);
    assertThat(message.getSeq()).isEqualTo(7L);
    assertThat(message.getSenderId()).isEqualTo(100L);
    assertThat(message.getMsgType()).isEqualTo(MessageAssembler.MSG_TYPE_TEXT);
    assertThat(message.getAbstractText()).isEqualTo("hello");
    assertThat(message.getStatus()).isEqualTo(MessageAssembler.STATUS_NORMAL);

    MsgPush push = assembler.toPush(ctx(), msgSend("client-1", "hello"), conv(), message);
    assertThat(push.getConvId()).isEqualTo(501L);
    assertThat(push.getSeq()).isEqualTo(7L);
    assertThat(push.getSender().getUserId()).isEqualTo(100L);
    assertThat(push.getContent().getText().getText()).isEqualTo("hello");
    assertThat(push.getExtOrThrow("msg_type")).isEqualTo("1");
    assertThat(push.getExtOrThrow("status")).isEqualTo("1");

    OutboxEntity outbox = assembler.msgSavedOutbox(1L, push);
    assertThat(outbox.getEventType()).isEqualTo("msg.saved");
    assertThat(outbox.getRoutingKey()).isEqualTo("msg.saved.1");
    MsgSavedEvent event = MsgSavedEvent.parseFrom(outbox.getPayload());
    assertThat(event.getTenantId()).isEqualTo(1L);
    assertThat(event.getConvId()).isEqualTo(501L);
    assertThat(event.getPushReady().getSeq()).isEqualTo(7L);
  }

  @Test
  void assemblesImageMessageAbstractAndType() {
    when(idGenerator.nextId()).thenReturn(9002L);
    MessageAssembler assembler = new MessageAssembler(
        idGenerator,
        Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneOffset.UTC));
    MsgSend request = MsgSend.newBuilder()
        .setClientMsgId("client-2")
        .setToUserId(200L)
        .setContent(MsgContent.newBuilder()
            .setImage(ImageContent.newBuilder()
                .setObjectKey("1/202606/a.png")
                .setMime("image/png")
                .setSize(512L)))
        .build();

    MessageEntity message = assembler.newMessage(ctx(), request, conv(), 8L);
    MsgPush push = assembler.toPush(ctx(), request, conv(), message);

    assertThat(message.getMsgType()).isEqualTo(MessageAssembler.MSG_TYPE_IMAGE);
    assertThat(message.getAbstractText()).isEqualTo("[image]");
    assertThat(push.getExtOrThrow("msg_type")).isEqualTo("2");
  }

  @Test
  void normalizesAndTruncatesAbstractText() {
    MessageAssembler assembler = new MessageAssembler(idGenerator);
    String text = " a\n b\t" + "x".repeat(300);

    assertThat(assembler.abstractText(text)).hasSize(255);
    assertThat(assembler.abstractText("  hello   world  ")).isEqualTo("hello world");
  }

  @Test
  void revokedStoredMessageHidesContentAndCarriesStatus() {
    MessageAssembler assembler = new MessageAssembler(
        idGenerator,
        Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneOffset.UTC));
    MessageEntity message = assembler.newTextMessage(ctx(), msgSend("client-1", "secret"),
        conv(), 7L);
    message.setStatus(MessageAssembler.STATUS_REVOKED);
    message.setRevokeReason(RevokeReason.BY_SENDER.getNumber());

    MsgPush push = assembler.toPush(message);

    assertThat(push.hasContent()).isFalse();
    assertThat(push.getExtOrThrow("status")).isEqualTo("2");
    assertThat(push.getExtOrThrow("revoke_reason")).isEqualTo("1");
  }

  private ConnCtx ctx() {
    return ConnCtx.newBuilder()
        .setTenantId(1L)
        .setUserId(100L)
        .setTraceId("trace-1")
        .build();
  }

  private ConvInfo conv() {
    return ConvInfo.newBuilder()
        .setConvId(501L)
        .setType(ConvType.C2C)
        .setPeerUserId(200L)
        .build();
  }

  private MsgSend msgSend(String clientMsgId, String text) {
    return MsgSend.newBuilder()
        .setClientMsgId(clientMsgId)
        .setToUserId(200L)
        .setContent(MsgContent.newBuilder()
            .setText(TextContent.newBuilder().setText(text)))
        .build();
  }
}
