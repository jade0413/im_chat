package com.im.message.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.im.message.moderation.dao.entity.ModerationLogEntity;
import com.im.message.moderation.dao.mapper.ModerationLogMapper;
import com.im.message.service.MessageRevokeService;
import com.im.proto.body.MsgPush;
import com.im.proto.common.MsgContent;
import com.im.proto.common.RevokeReason;
import com.im.proto.common.TextContent;
import com.im.proto.common.FileContent;
import com.im.proto.common.ImageContent;
import com.im.proto.common.VoiceContent;
import com.im.proto.events.MsgSavedEvent;
import java.math.BigDecimal;
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
  private MediaModerationProvider mediaModerationProvider;

  @Mock
  private ModerationLogMapper moderationLogMapper;

  @Mock
  private MessageRevokeService messageRevokeService;

  @Captor
  private ArgumentCaptor<ModerationLogEntity> logCaptor;

  @Captor
  private ArgumentCaptor<MediaModerationRequest> mediaRequestCaptor;

  private ModerationService service;

  @BeforeEach
  void setUp() {
    service = new ModerationService(
        sensitiveWordService,
        mediaModerationProvider,
        moderationLogMapper,
        messageRevokeService,
        new ModerationProperties(true, Duration.ofHours(24), "", "", null),
        Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneOffset.UTC));
    lenient().when(mediaModerationProvider.scan(org.mockito.Mockito.any()))
        .thenReturn(Optional.empty());
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

    assertThat(result).isEqualTo(ModerationResult.CLEAN);
    verify(sensitiveWordService, never()).findRevokeMatch(
        org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyString());
  }

  @Test
  void routesImageToMediaModerationProvider() {
    MsgSavedEvent event = MsgSavedEvent.newBuilder()
        .setTenantId(1L)
        .setConvId(501L)
        .setSeq(3L)
        .setServerMsgId(9003L)
        .setPushReady(MsgPush.newBuilder()
            .setContent(MsgContent.newBuilder()
                .setImage(ImageContent.newBuilder()
                    .setObjectKey("1/202606/a.png")
                    .setMime("image/png")
                    .setSize(512L))))
        .build();

    ModerationResult result = service.moderate(event);

    assertThat(result).isEqualTo(ModerationResult.CLEAN);
    verify(mediaModerationProvider).scan(mediaRequestCaptor.capture());
    MediaModerationRequest request = mediaRequestCaptor.getValue();
    assertThat(request.objectKey()).isEqualTo("1/202606/a.png");
    assertThat(request.mime()).isEqualTo("image/png");
    assertThat(request.size()).isEqualTo(512L);
    assertThat(request.mediaType()).isEqualTo("image");
  }

  @Test
  void normalizesVoiceCodecToAudioMimeForMediaModerationProvider() {
    MsgSavedEvent event = MsgSavedEvent.newBuilder()
        .setTenantId(1L)
        .setConvId(501L)
        .setSeq(3L)
        .setServerMsgId(9003L)
        .setPushReady(MsgPush.newBuilder()
            .setContent(MsgContent.newBuilder()
                .setVoice(VoiceContent.newBuilder()
                    .setObjectKey("1/202606/a.m4a")
                    .setDurationMs(2000)
                    .setSize(256L)
                    .setCodec("aac"))))
        .build();

    ModerationResult result = service.moderate(event);

    assertThat(result).isEqualTo(ModerationResult.CLEAN);
    verify(mediaModerationProvider).scan(mediaRequestCaptor.capture());
    MediaModerationRequest request = mediaRequestCaptor.getValue();
    assertThat(request.objectKey()).isEqualTo("1/202606/a.m4a");
    assertThat(request.mime()).isEqualTo("audio/aac");
    assertThat(request.size()).isEqualTo(256L);
    assertThat(request.mediaType()).isEqualTo("voice");
  }

  @Test
  void routesVideoFileToMediaModerationProvider() {
    MsgSavedEvent event = fileEvent("1/202606/a.mp4", "a.mp4", "video/mp4", 4096L);

    ModerationResult result = service.moderate(event);

    assertThat(result).isEqualTo(ModerationResult.CLEAN);
    verify(mediaModerationProvider).scan(mediaRequestCaptor.capture());
    MediaModerationRequest request = mediaRequestCaptor.getValue();
    assertThat(request.objectKey()).isEqualTo("1/202606/a.mp4");
    assertThat(request.mime()).isEqualTo("video/mp4");
    assertThat(request.size()).isEqualTo(4096L);
    assertThat(request.mediaType()).isEqualTo("video");
  }

  @Test
  void routesDocumentFileToMediaModerationProvider() {
    MsgSavedEvent event = fileEvent("1/202606/a.pdf", "a.pdf", "application/pdf", 2048L);

    ModerationResult result = service.moderate(event);

    assertThat(result).isEqualTo(ModerationResult.CLEAN);
    verify(mediaModerationProvider).scan(mediaRequestCaptor.capture());
    MediaModerationRequest request = mediaRequestCaptor.getValue();
    assertThat(request.objectKey()).isEqualTo("1/202606/a.pdf");
    assertThat(request.mime()).isEqualTo("application/pdf");
    assertThat(request.size()).isEqualTo(2048L);
    assertThat(request.mediaType()).isEqualTo("file");
  }

  @Test
  void revokesAndWritesAuditLogWhenMediaProviderFlagsFile() {
    MsgSavedEvent event = fileEvent("video/mp4");
    when(mediaModerationProvider.scan(org.mockito.Mockito.any()))
        .thenReturn(Optional.of(new MediaModerationMatch(
            ModerationConstants.PROVIDER_FILE_STATUS,
            "video",
            BigDecimal.ONE.setScale(4),
            "video:1/202606/a.mp4")));
    when(messageRevokeService.revokeIfNeeded(501L, 3L, RevokeReason.BY_MODERATION, 0L))
        .thenReturn(true);

    ModerationResult result = service.moderate(event);

    assertThat(result).isEqualTo(ModerationResult.REVOKED);
    verify(moderationLogMapper).insert(logCaptor.capture());
    ModerationLogEntity log = logCaptor.getValue();
    assertThat(log.getProvider()).isEqualTo(ModerationConstants.PROVIDER_FILE_STATUS);
    assertThat(log.getCategory()).isEqualTo("video");
    assertThat(log.getOriginalContent()).isEqualTo("video:1/202606/a.mp4");
    assertThat(log.getActionTaken()).isEqualTo(ModerationConstants.ACTION_REVOKE);
  }

  @Test
  void skipsDuplicateMediaProviderLog() {
    MsgSavedEvent event = fileEvent("video/mp4");
    when(mediaModerationProvider.scan(org.mockito.Mockito.any()))
        .thenReturn(Optional.of(new MediaModerationMatch(
            ModerationConstants.PROVIDER_FILE_STATUS,
            "video",
            BigDecimal.ONE.setScale(4),
            "video:1/202606/a.mp4")));
    when(moderationLogMapper.selectByMessageAndProvider(
        1L, 9003L, ModerationConstants.PROVIDER_FILE_STATUS))
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
  void skipsAlreadyModeratedMediaBeforeCallingProvider() {
    MsgSavedEvent event = fileEvent("video/mp4");
    when(moderationLogMapper.selectFirstByMessage(1L, 9003L))
        .thenReturn(new ModerationLogEntity());

    ModerationResult result = service.moderate(event);

    assertThat(result).isEqualTo(ModerationResult.DUPLICATE);
    verify(mediaModerationProvider, never()).scan(org.mockito.Mockito.any());
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

  private MsgSavedEvent fileEvent(String mime) {
    return fileEvent("1/202606/a.mp4", "a.mp4", mime, 100L);
  }

  private MsgSavedEvent fileEvent(String objectKey, String fileName, String mime, long size) {
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
                .setFile(FileContent.newBuilder()
                    .setObjectKey(objectKey)
                    .setFileName(fileName)
                    .setSize(size)
                    .setMime(mime))))
        .build();
  }
}
