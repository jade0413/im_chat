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
import com.im.proto.common.MsgContent;
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

    OutboxEntity outbox = assembler.msgSavedOutbox(1L, push);
    assertThat(outbox.getEventType()).isEqualTo("msg.saved");
    assertThat(outbox.getRoutingKey()).isEqualTo("msg.saved.1");
    MsgSavedEvent event = MsgSavedEvent.parseFrom(outbox.getPayload());
    assertThat(event.getTenantId()).isEqualTo(1L);
    assertThat(event.getConvId()).isEqualTo(501L);
    assertThat(event.getPushReady().getSeq()).isEqualTo(7L);
  }

  @Test
  void normalizesAndTruncatesAbstractText() {
    MessageAssembler assembler = new MessageAssembler(idGenerator);
    String text = " a\n b\t" + "x".repeat(300);

    assertThat(assembler.abstractText(text)).hasSize(255);
    assertThat(assembler.abstractText("  hello   world  ")).isEqualTo("hello world");
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
