package com.im.message.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.im.message.moderation.dao.entity.ModerationLogEntity;
import com.im.message.moderation.dao.mapper.ModerationLogMapper;
import com.im.message.service.MessageRevokeService;
import com.im.proto.body.MsgPush;
import com.im.proto.common.MsgContent;
import com.im.proto.common.RevokeReason;
import com.im.proto.common.TextContent;
import com.im.proto.events.MsgSavedEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

  @Mock
  private SensitiveWordService sensitiveWordService;

  @Mock
  private ModerationLogMapper moderationLogMapper;

  @Mock
  private MessageRevokeService messageRevokeService;

  @Captor
  private ArgumentCaptor<ModerationLogEntity> logCaptor;

  private ModerationService service;

  @BeforeEach
  void setUp() {
    service = new ModerationService(
        sensitiveWordService,
        moderationLogMapper,
        messageRevokeService,
        new ModerationProperties(true, Duration.ofHours(24), "", ""),
        Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void revokesAndWritesAuditLogWhenTextHitsSensitiveWord() {
    MsgSavedEvent event = textEvent("contains bad word");
    when(sensitiveWordService.findRevokeMatch(1L, "contains bad word"))
        .thenReturn(Optional.of(new ModerationMatch("bad", "abuse", 1)));
    when(messageRevokeService.revokeIfNeeded(501L, 3L, RevokeReason.BY_MODERATION, 0L))
        .thenReturn(true);

    ModerationResult result = service.moderate(event);

    assertThat(result).isEqualTo(ModerationResult.REVOKED);
    verify(moderationLogMapper).insert(logCaptor.capture());
    ModerationLogEntity log = logCaptor.getValue();
    assertThat(log.getTenantId()).isEqualTo(1L);
    assertThat(log.getMessageId()).isEqualTo(9003L);
    assertThat(log.getProvider()).isEqualTo(ModerationConstants.PROVIDER_DFA);
    assertThat(log.getCategory()).isEqualTo("abuse");
    assertThat(log.getActionTaken()).isEqualTo(ModerationConstants.ACTION_REVOKE);
    assertThat(log.getOriginalContent()).isEqualTo("contains bad word");
    assertThat(log.getAuditStatus()).isEqualTo(ModerationConstants.AUDIT_STATUS_AUTO);
  }

  @Test
  void skipsDuplicateModerationLog() {
    MsgSavedEvent event = textEvent("contains bad word");
    when(moderationLogMapper.selectByMessageAndProvider(1L, 9003L, ModerationConstants.PROVIDER_DFA))
        .thenReturn(new ModerationLogEntity());

    ModerationResult result = service.moderate(event);

    assertThat(result).isEqualTo(ModerationResult.DUPLICATE);
    verify(messageRevokeService, never()).revokeIfNeeded(
        org.mockito.Mockito.anyLong(),
        org.mockito.Mockito.anyLong(),
        org.mockito.Mockito.any(),
        org.mockito.Mockito.anyLong());
  }

  @Test
  void skipsNonTextMessage() {
    MsgSavedEvent event = MsgSavedEvent.newBuilder()
        .setTenantId(1L)
        .setConvId(501L)
        .setSeq(3L)
        .setServerMsgId(9003L)
        .setPushReady(MsgPush.newBuilder()
            .setContent(MsgContent.newBuilder()
                .setFile(com.im.proto.common.FileContent.newBuilder()
                    .setObjectKey("1/202606/file")
                    .setFileName("a.txt")
                    .setSize(1)
                    .setMime("text/plain"))))
        .build();

    ModerationResult result = service.moderate(event);

    assertThat(result).isEqualTo(ModerationResult.SKIPPED);
    verify(sensitiveWordService, never()).findRevokeMatch(
        org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyString());
  }

  @Test
  void doesNotWriteLogWhenMessageWasAlreadyRevoked() {
    MsgSavedEvent event = textEvent("contains bad word");
    when(sensitiveWordService.findRevokeMatch(1L, "contains bad word"))
        .thenReturn(Optional.of(new ModerationMatch("bad", "abuse", 1)));
    when(messageRevokeService.revokeIfNeeded(501L, 3L, RevokeReason.BY_MODERATION, 0L))
        .thenReturn(false);

    ModerationResult result = service.moderate(event);

    assertThat(result).isEqualTo(ModerationResult.ALREADY_REVOKED);
    verify(moderationLogMapper, never()).insert(org.mockito.Mockito.any(ModerationLogEntity.class));
  }

  private MsgSavedEvent textEvent(String text) {
    return MsgSavedEvent.newBuilder()
        .setTenantId(1L)
        .setConvId(501L)
        .setSeq(3L)
        .setServerMsgId(9003L)
        .setPushReady(MsgPush.newBuilder()
            .setConvId(501L)
            .setSeq(3L)
            .setServerMsgId(9003L)
            .setContent(MsgContent.newBuilder()
                .setText(TextContent.newBuilder().setText(text))))
        .build();
  }
}
