package com.im.message.moderation;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.im.proto.body.MsgPush;
import com.im.proto.common.MsgContent;
import com.im.proto.common.TextContent;
import com.im.proto.events.MsgSavedEvent;
import com.im.proto.events.WordReloadEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MsgSavedModerationConsumerTest {

  @Mock
  private ModerationService moderationService;

  @Mock
  private SensitiveWordService sensitiveWordService;

  @Mock
  private ModerationEventDeduplicator deduplicator;

  @Test
  void moderatesAndMarksSavedEvent() {
    MsgSavedEvent event = event();
    when(deduplicator.isMarked(1L, 10L)).thenReturn(false);

    new MsgSavedModerationConsumer(moderationService, sensitiveWordService, deduplicator)
        .handleMsgSaved(10L, event);

    verify(moderationService).moderate(event);
    verify(deduplicator).mark(1L, 10L);
  }

  @Test
  void skipsMarkedEvent() {
    MsgSavedEvent event = event();
    when(deduplicator.isMarked(1L, 10L)).thenReturn(true);

    new MsgSavedModerationConsumer(moderationService, sensitiveWordService, deduplicator)
        .handleMsgSaved(10L, event);

    verify(moderationService, never()).moderate(event);
    verify(deduplicator, never()).mark(1L, 10L);
  }

  @Test
  void reloadsSensitiveWordsFromEventPayload() throws Exception {
    byte[] payload = WordReloadEvent.newBuilder().setTenantId(2L).build().toByteArray();

    new MsgSavedModerationConsumer(moderationService, sensitiveWordService, deduplicator)
        .onWordReload(payload);

    verify(sensitiveWordService).reload(2L);
  }

  private MsgSavedEvent event() {
    return MsgSavedEvent.newBuilder()
        .setTenantId(1L)
        .setConvId(501L)
        .setSeq(3L)
        .setServerMsgId(9003L)
        .setPushReady(MsgPush.newBuilder()
            .setContent(MsgContent.newBuilder()
                .setText(TextContent.newBuilder().setText("hello"))))
        .build();
  }
}
