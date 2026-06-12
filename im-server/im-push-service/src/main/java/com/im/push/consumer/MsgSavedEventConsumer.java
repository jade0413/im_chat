package com.im.push.consumer;

import com.im.common.tenant.TenantContext;
import com.im.proto.events.MsgSavedEvent;
import com.im.proto.ws.Cmd;
import com.im.push.service.ConversationMemberClient;
import com.im.push.service.PushDispatchService;
import com.im.push.service.PushEventDeduplicator;
import java.util.List;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
public class MsgSavedEventConsumer {

  private final ConversationMemberClient conversationMemberClient;
  private final PushDispatchService pushDispatchService;
  private final PushEventDeduplicator deduplicator;

  public MsgSavedEventConsumer(ConversationMemberClient conversationMemberClient,
      PushDispatchService pushDispatchService,
      PushEventDeduplicator deduplicator) {
    this.conversationMemberClient = conversationMemberClient;
    this.pushDispatchService = pushDispatchService;
    this.deduplicator = deduplicator;
  }

  @RabbitListener(queues = "${im.push.msg-saved-queue:im.push.msg.saved}")
  public void onMessage(byte[] payload,
      @Header(name = "event_id", required = false) Long eventId) throws Exception {
    MsgSavedEvent event = MsgSavedEvent.parseFrom(payload);
    long dedupId = eventId == null || eventId <= 0 ? event.getServerMsgId() : eventId;
    handle(dedupId, event);
  }

  void handle(long eventId, MsgSavedEvent event) {
    TenantContext.runWithTenant(event.getTenantId(), () -> {
      if (!deduplicator.tryMark(event.getTenantId(), eventId)) {
        return;
      }
      List<Long> memberUserIds = conversationMemberClient.getMemberUserIds(event.getConvId());
      pushDispatchService.pushToUsers(
          event.getTenantId(),
          memberUserIds,
          Cmd.MSG_PUSH_VALUE,
          event.getPushReady().toByteArray(),
          true);
    });
  }
}
