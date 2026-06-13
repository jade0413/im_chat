package com.im.push.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.im.proto.body.RevokeNotify;
import com.im.proto.common.RevokeReason;
import com.im.proto.events.MsgRevokedEvent;
import com.im.proto.ws.Cmd;
import com.im.push.service.ConversationMemberClient;
import com.im.push.service.PushDispatchService;
import com.im.push.service.PushEventDeduplicator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MsgRevokedEventConsumerTest {

  @Mock
  private ConversationMemberClient conversationMemberClient;

  @Mock
  private PushDispatchService pushDispatchService;

  @Mock
  private PushEventDeduplicator deduplicator;

  @Test
  void pushesRevokeNotifyToConversationMembersOnce() throws Exception {
    MsgRevokedEvent event = event();
    when(deduplicator.tryMark(1L, 10L)).thenReturn(true);
    when(conversationMemberClient.getMemberUserIds(501L)).thenReturn(List.of(100L, 200L));

    new MsgRevokedEventConsumer(conversationMemberClient, pushDispatchService, deduplicator)
        .handle(10L, event);

    ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(pushDispatchService).pushToUsers(
        org.mockito.Mockito.eq(1L),
        org.mockito.Mockito.eq(List.of(100L, 200L)),
        org.mockito.Mockito.eq(Cmd.REVOKE_NOTIFY_VALUE),
        bodyCaptor.capture(),
        org.mockito.Mockito.eq(true));
    RevokeNotify notify = RevokeNotify.parseFrom(bodyCaptor.getValue());
    assertThat(notify.getConvId()).isEqualTo(501L);
    assertThat(notify.getSeq()).isEqualTo(3L);
    assertThat(notify.getServerMsgId()).isEqualTo(9003L);
    assertThat(notify.getReason()).isEqualTo(RevokeReason.BY_SENDER);
    assertThat(notify.getOperatorUserId()).isEqualTo(100L);
  }

  @Test
  void skipsDuplicateRevokedEvent() {
    MsgRevokedEvent event = event();
    when(deduplicator.tryMark(1L, 10L)).thenReturn(false);

    new MsgRevokedEventConsumer(conversationMemberClient, pushDispatchService, deduplicator)
        .handle(10L, event);

    verify(conversationMemberClient, never()).getMemberUserIds(org.mockito.Mockito.anyLong());
    verify(pushDispatchService, never()).pushToUsers(
        org.mockito.Mockito.anyLong(),
        org.mockito.Mockito.anyCollection(),
        org.mockito.Mockito.anyInt(),
        org.mockito.Mockito.any(),
        org.mockito.Mockito.anyBoolean());
  }

  private MsgRevokedEvent event() {
    return MsgRevokedEvent.newBuilder()
        .setTenantId(1L)
        .setConvId(501L)
        .setSeq(3L)
        .setServerMsgId(9003L)
        .setReason(RevokeReason.BY_SENDER.getNumber())
        .setOperatorUserId(100L)
        .build();
  }
}
