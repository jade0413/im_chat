package com.im.push.consumer;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.im.proto.body.MsgPush;
import com.im.proto.common.ConvType;
import com.im.proto.events.MsgSavedEvent;
import com.im.proto.ws.Cmd;
import com.im.push.service.ConversationMemberClient;
import com.im.push.service.ConversationMemberClient.ConvMembersResult;
import com.im.push.service.OnlineAgentClient;
import com.im.push.service.PushDispatchService;
import com.im.push.service.PushEventDeduplicator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MsgSavedEventConsumerTest {

  @Mock
  private ConversationMemberClient conversationMemberClient;

  @Mock
  private OnlineAgentClient onlineAgentClient;

  @Mock
  private PushDispatchService pushDispatchService;

  @Mock
  private PushEventDeduplicator deduplicator;

  @Test
  void pushesSavedMessageToConversationMembersOnce() {
    MsgSavedEvent event = event();
    when(deduplicator.tryMark(1L, 10L)).thenReturn(true);
    when(conversationMemberClient.getMembersResult(501L))
        .thenReturn(new ConvMembersResult(
            List.of(100L, 200L),
            ConvType.C2C.getNumber(),
            0,
            0L));

    new MsgSavedEventConsumer(conversationMemberClient, onlineAgentClient, pushDispatchService, deduplicator)
        .handle(10L, event);

    verify(pushDispatchService).pushToUsers(
        1L,
        List.of(100L, 200L),
        Cmd.MSG_PUSH_VALUE,
        event.getPushReady().toByteArray(),
        true);
  }

  @Test
  void skipsDuplicateSavedEvent() {
    MsgSavedEvent event = event();
    when(deduplicator.tryMark(1L, 10L)).thenReturn(false);

    new MsgSavedEventConsumer(conversationMemberClient, onlineAgentClient, pushDispatchService, deduplicator)
        .handle(10L, event);

    verify(conversationMemberClient, never()).getMemberUserIds(org.mockito.Mockito.anyLong());
    verify(conversationMemberClient, never()).getMembersResult(org.mockito.Mockito.anyLong());
    verify(pushDispatchService, never()).pushToUsers(
        org.mockito.Mockito.anyLong(),
        org.mockito.Mockito.anyCollection(),
        org.mockito.Mockito.anyInt(),
        org.mockito.Mockito.any(),
        org.mockito.Mockito.anyBoolean());
  }

  private MsgSavedEvent event() {
    return MsgSavedEvent.newBuilder()
        .setTenantId(1L)
        .setConvId(501L)
        .setSeq(1L)
        .setServerMsgId(9001L)
        .setSenderId(100L)
        .setPushReady(MsgPush.newBuilder()
            .setConvId(501L)
            .setSeq(1L)
            .setServerMsgId(9001L))
        .build();
  }
}
