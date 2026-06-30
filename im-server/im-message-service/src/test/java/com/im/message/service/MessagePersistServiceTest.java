package com.im.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.im.common.id.SnowflakeIdGenerator;
import com.im.common.outbox.OutboxWriter;
import com.im.common.sequence.ConversationSequenceService;
import com.im.message.dao.entity.MessageEntity;
import com.im.message.dao.mapper.ConversationProgressMapper;
import com.im.message.dao.mapper.MessageMapper;
import com.im.proto.body.ConvInfo;
import com.im.proto.body.MsgSend;
import com.im.proto.common.ConvType;
import com.im.proto.common.MsgContent;
import com.im.proto.common.TextContent;
import com.im.proto.events.MsgSavedEvent;
import com.im.proto.rpc.ConnCtx;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessagePersistServiceTest {

  @Mock
  private MessageMapper messageMapper;

  @Mock
  private ConversationProgressMapper conversationProgressMapper;

  @Mock
  private ConversationSequenceService sequenceService;

  @Mock
  private OutboxWriter outboxWriter;

  @Mock
  private SnowflakeIdGenerator idGenerator;

  private final MsgSavedEventFactory msgSavedEventFactory = new MsgSavedEventFactory();

  @Captor
  private ArgumentCaptor<MessageEntity> messageCaptor;

  @Captor
  private ArgumentCaptor<byte[]> payloadCaptor;

  @Test
  void persistsMessageConversationProgressAndOutboxInOneServiceCall() throws Exception {
    when(idGenerator.nextId()).thenReturn(9001L);
    when(sequenceService.nextSeq(501L)).thenReturn(8L);
    when(messageMapper.insert(any(MessageEntity.class))).thenReturn(1);
    when(conversationProgressMapper.updateLastMessage(anyLong(), anyLong(), any(String.class),
        any(LocalDateTime.class))).thenReturn(1);
    MessageAssembler assembler = new MessageAssembler(
        idGenerator,
        Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneOffset.UTC));

    MessageSendResult result = new MessagePersistService(
        messageMapper,
        conversationProgressMapper,
        sequenceService,
        outboxWriter,
        assembler,
        msgSavedEventFactory).persist(1L, ctx(), request(), conv());

    assertThat(result.serverMsgId()).isEqualTo(9001L);
    assertThat(result.conversationId()).isEqualTo(501L);
    assertThat(result.seq()).isEqualTo(8L);

    verify(messageMapper).insert(messageCaptor.capture());
    assertThat(messageCaptor.getValue().getClientMsgId()).isEqualTo("client-1");
    assertThat(messageCaptor.getValue().getAbstractText()).isEqualTo("hello");

    verify(conversationProgressMapper).updateLastMessage(
        501L,
        8L,
        "hello",
        messageCaptor.getValue().getCreatedAt());

    verify(outboxWriter).write(eq(1L), eq("msg.saved"), eq("msg.saved.1"), payloadCaptor.capture());
    MsgSavedEvent event = MsgSavedEvent.parseFrom(payloadCaptor.getValue());
    assertThat(event.getTenantId()).isEqualTo(1L);
    assertThat(event.getConvId()).isEqualTo(501L);
    assertThat(event.getSeq()).isEqualTo(8L);
    assertThat(event.getServerMsgId()).isEqualTo(9001L);
    assertThat(event.getSenderConnId()).isEqualTo("conn-100");
  }

  private ConnCtx ctx() {
    return ConnCtx.newBuilder().setTenantId(1L).setUserId(100L).setConnId("conn-100").build();
  }

  private ConvInfo conv() {
    return ConvInfo.newBuilder().setConvId(501L).setType(ConvType.C2C).build();
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
