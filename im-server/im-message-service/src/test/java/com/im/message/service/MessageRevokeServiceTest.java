package com.im.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.outbox.OutboxWriter;
import com.im.common.tenant.TenantContext;
import com.im.message.dao.entity.MessageEntity;
import com.im.message.dao.mapper.ConversationProgressMapper;
import com.im.message.dao.mapper.MessageMapper;
import com.im.proto.common.ConvType;
import com.im.proto.common.RevokeReason;
import com.im.proto.events.MsgRevokedEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageRevokeServiceTest {

  @Mock
  private MessageMapper messageMapper;

  @Mock
  private ConversationProgressMapper conversationProgressMapper;

  @Mock
  private ConversationMemberClient memberClient;

  @Mock
  private OutboxWriter outboxWriter;

  @Captor
  private ArgumentCaptor<byte[]> payloadCaptor;

  private MessageRevokeService service;

  @BeforeEach
  void setUp() {
    service = new MessageRevokeService(
        messageMapper,
        conversationProgressMapper,
        memberClient,
        outboxWriter,
        new MsgRevokedEventFactory(),
        Clock.fixed(Instant.parse("2026-06-13T00:01:00Z"), ZoneOffset.UTC));
  }

  @Test
  void senderRevokesMessageAndWritesOutbox() throws Exception {
    when(messageMapper.selectByConversationSeq(1L, 501L, 3L)).thenReturn(message(1));
    when(memberClient.getMemberConv(100L, 501L)).thenReturn(com.im.proto.body.ConvInfo.newBuilder()
        .setConvId(501L)
        .setType(ConvType.C2C)
        .build());
    when(messageMapper.markRevoked(1L, 501L, 3L, MessageAssembler.STATUS_REVOKED,
        RevokeReason.BY_SENDER.getNumber())).thenReturn(1);

    revokeWithTenant(RevokeReason.BY_SENDER, 100L);

    verify(outboxWriter).write(org.mockito.Mockito.eq(1L), org.mockito.Mockito.eq("msg.revoked"),
        org.mockito.Mockito.eq("msg.revoked.1"), payloadCaptor.capture());
    verify(conversationProgressMapper).updateLastMessageAbstractIfLatest(
        1L, 501L, 3L, "message revoked");
    MsgRevokedEvent event = MsgRevokedEvent.parseFrom(payloadCaptor.getValue());
    assertThat(event.getTenantId()).isEqualTo(1L);
    assertThat(event.getConvId()).isEqualTo(501L);
    assertThat(event.getSeq()).isEqualTo(3L);
    assertThat(event.getServerMsgId()).isEqualTo(9003L);
    assertThat(event.getReason()).isEqualTo(RevokeReason.BY_SENDER.getNumber());
    assertThat(event.getOperatorUserId()).isEqualTo(100L);
  }

  @Test
  void repeatedRevokeIsIdempotentWithoutOutbox() {
    when(messageMapper.selectByConversationSeq(1L, 501L, 3L))
        .thenReturn(message(MessageAssembler.STATUS_REVOKED));

    revokeWithTenant(RevokeReason.BY_SENDER, 100L);

    verify(messageMapper, never()).markRevoked(
        org.mockito.Mockito.anyLong(),
        org.mockito.Mockito.anyLong(),
        org.mockito.Mockito.anyLong(),
        org.mockito.Mockito.anyInt(),
        org.mockito.Mockito.anyInt());
    verify(outboxWriter, never()).write(org.mockito.Mockito.anyLong(),
        org.mockito.Mockito.anyString(),
        org.mockito.Mockito.anyString(),
        org.mockito.Mockito.any());
  }

  @Test
  void rejectsSenderRevokeByOtherUser() {
    when(messageMapper.selectByConversationSeq(1L, 501L, 3L)).thenReturn(message(1));
    when(memberClient.getMemberConv(200L, 501L)).thenReturn(com.im.proto.body.ConvInfo.newBuilder()
        .setConvId(501L)
        .setType(ConvType.C2C)
        .build());

    assertThatThrownBy(() -> revokeWithTenant(RevokeReason.BY_SENDER, 200L))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NO_PERMISSION);
    verify(outboxWriter, never()).write(org.mockito.Mockito.anyLong(),
        org.mockito.Mockito.anyString(),
        org.mockito.Mockito.anyString(),
        org.mockito.Mockito.any());
  }

  @Test
  void rejectsSenderRevokeAfterWindow() {
    MessageEntity message = message(1);
    message.setCreatedAt(LocalDateTime.of(2026, 6, 12, 23, 58));
    when(messageMapper.selectByConversationSeq(1L, 501L, 3L)).thenReturn(message);
    when(memberClient.getMemberConv(100L, 501L)).thenReturn(com.im.proto.body.ConvInfo.newBuilder()
        .setConvId(501L)
        .setType(ConvType.C2C)
        .build());

    assertThatThrownBy(() -> revokeWithTenant(RevokeReason.BY_SENDER, 100L))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.REVOKE_WINDOW_EXPIRED);
  }

  @Test
  void adminCanRevokeWithoutSenderWindow() {
    MessageEntity message = message(1);
    message.setCreatedAt(LocalDateTime.of(2026, 6, 12, 23, 58));
    when(messageMapper.selectByConversationSeq(1L, 501L, 3L)).thenReturn(message);
    when(messageMapper.markRevoked(1L, 501L, 3L, MessageAssembler.STATUS_REVOKED,
        RevokeReason.BY_ADMIN.getNumber())).thenReturn(1);

    revokeWithTenant(RevokeReason.BY_ADMIN, 900L);

    verify(memberClient, never()).getMemberConv(org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyLong());
    verify(outboxWriter).write(org.mockito.Mockito.eq(1L), org.mockito.Mockito.eq("msg.revoked"),
        org.mockito.Mockito.eq("msg.revoked.1"), org.mockito.Mockito.any());
  }

  private void revokeWithTenant(RevokeReason reason, long operatorUserId) {
    TenantContext.runWithTenant(1L, () -> {
      service.revoke(501L, 3L, reason, operatorUserId);
    });
  }

  private MessageEntity message(int status) {
    MessageEntity message = new MessageEntity();
    message.setId(9003L);
    message.setTenantId(1L);
    message.setConversationId(501L);
    message.setSeq(3L);
    message.setSenderId(100L);
    message.setClientMsgId("client-3");
    message.setStatus(status);
    message.setCreatedAt(LocalDateTime.of(2026, 6, 13, 0, 0, 30));
    return message;
  }
}
