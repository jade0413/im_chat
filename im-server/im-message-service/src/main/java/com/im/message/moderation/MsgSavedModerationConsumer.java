package com.im.message.moderation;

import com.im.common.tenant.TenantContext;
import com.im.proto.events.MsgSavedEvent;
import com.im.proto.events.WordReloadEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
public class MsgSavedModerationConsumer {

  private final ModerationService moderationService;
  private final SensitiveWordService sensitiveWordService;
  private final ModerationEventDeduplicator deduplicator;

  public MsgSavedModerationConsumer(ModerationService moderationService,
      SensitiveWordService sensitiveWordService,
      ModerationEventDeduplicator deduplicator) {
    this.moderationService = moderationService;
    this.sensitiveWordService = sensitiveWordService;
    this.deduplicator = deduplicator;
  }

  @RabbitListener(queues = "${im.moderation.msg-saved-queue:im.moderation.msg.saved}")
  public void onMsgSaved(byte[] payload,
      @Header(name = "event_id", required = false) Long eventId) throws Exception {
    MsgSavedEvent event = MsgSavedEvent.parseFrom(payload);
    long dedupId = eventId == null || eventId <= 0 ? event.getServerMsgId() : eventId;
    handleMsgSaved(dedupId, event);
  }

  @RabbitListener(queues = "${im.moderation.word-reload-queue:im.moderation.word.reload}")
  public void onWordReload(byte[] payload) throws Exception {
    WordReloadEvent event = WordReloadEvent.parseFrom(payload);
    sensitiveWordService.reload(event.getTenantId());
  }

  void handleMsgSaved(long eventId, MsgSavedEvent event) {
    TenantContext.runWithTenant(event.getTenantId(), () -> {
      if (deduplicator.isMarked(event.getTenantId(), eventId)) {
        return;
      }
      moderationService.moderate(event);
      deduplicator.mark(event.getTenantId(), eventId);
    });
  }
}
