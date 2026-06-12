package com.im.message.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.im.proto.body.MsgPush;
import com.im.proto.body.Sender;
import com.im.proto.common.MsgContent;
import com.im.proto.common.TextContent;
import com.im.proto.events.MsgSavedEvent;
import org.junit.jupiter.api.Test;

class MsgSavedEventFactoryTest {

  private final MsgSavedEventFactory factory = new MsgSavedEventFactory();

  @Test
  void createsMsgSavedOutboxPayload() throws Exception {
    MsgPush push = MsgPush.newBuilder()
        .setConvId(501L)
        .setSeq(7L)
        .setServerMsgId(9001L)
        .setSender(Sender.newBuilder().setUserId(100L))
        .setContent(MsgContent.newBuilder()
            .setText(TextContent.newBuilder().setText("hello")))
        .build();

    MsgSavedEventFactory.MsgSavedOutboxEvent event = factory.create(1L, push);

    assertThat(event.eventType()).isEqualTo("msg.saved");
    assertThat(event.routingKey()).isEqualTo("msg.saved.1");
    MsgSavedEvent payload = MsgSavedEvent.parseFrom(event.payload());
    assertThat(payload.getTenantId()).isEqualTo(1L);
    assertThat(payload.getConvId()).isEqualTo(501L);
    assertThat(payload.getPushReady().getContent().getText().getText()).isEqualTo("hello");
  }
}
