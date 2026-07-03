package com.im.message.moderation;

import com.im.message.moderation.dao.entity.ModerationLogEntity;
import com.im.message.moderation.dao.mapper.ModerationLogMapper;
import com.im.message.service.MessageRevokeService;
import com.im.proto.common.MsgContent;
import com.im.proto.common.RevokeReason;
import com.im.proto.events.MsgSavedEvent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationService {

  private static final BigDecimal EXACT_MATCH_SCORE = BigDecimal.ONE.setScale(4);

  private final SensitiveWordService sensitiveWordService;
  private final MediaModerationProvider mediaModerationProvider;
  private final ModerationLogMapper moderationLogMapper;
  private final MessageRevokeService messageRevokeService;
  private final ModerationProperties properties;
  private final Clock clock;

  public ModerationService(SensitiveWordService sensitiveWordService,
      MediaModerationProvider mediaModerationProvider,
      ModerationLogMapper moderationLogMapper,
      MessageRevokeService messageRevokeService,
      ModerationProperties properties,
      @Qualifier("moderationClock") Clock clock) {
    this.sensitiveWordService = sensitiveWordService;
    this.mediaModerationProvider = mediaModerationProvider;
    this.moderationLogMapper = moderationLogMapper;
    this.messageRevokeService = messageRevokeService;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public ModerationResult moderate(MsgSavedEvent event) {
    if (!properties.isEnabled()) {
      return ModerationResult.DISABLED;
    }
    MsgContent content = event.getPushReady().getContent();
    return switch (content.getContentCase()) {
      case TEXT -> moderateText(event, content);
      case IMAGE -> moderateMedia(event, new MediaModerationRequest(
          event.getTenantId(),
          event.getServerMsgId(),
          content.getImage().getObjectKey(),
          content.getImage().getMime(),
          content.getImage().getSize(),
          "image"));
      case VOICE -> moderateMedia(event, new MediaModerationRequest(
          event.getTenantId(),
          event.getServerMsgId(),
          content.getVoice().getObjectKey(),
          voiceMime(content.getVoice().getCodec()),
          content.getVoice().getSize(),
          "voice"));
      case FILE -> moderateMedia(event, new MediaModerationRequest(
          event.getTenantId(),
          event.getServerMsgId(),
          content.getFile().getObjectKey(),
          content.getFile().getMime(),
          content.getFile().getSize(),
          mediaType(content.getFile().getMime())));
      default -> ModerationResult.SKIPPED;
    };
  }

  private ModerationResult moderateText(MsgSavedEvent event, MsgContent content) {
    if (hasProcessed(event, ModerationConstants.PROVIDER_DFA)) {
      return ModerationResult.DUPLICATE;
    }
    String originalText = content.getText().getText();
    Optional<ModerationMatch> match = sensitiveWordService.findRevokeMatch(
        event.getTenantId(), originalText);
    if (match.isEmpty()) {
      return ModerationResult.CLEAN;
    }
    boolean revoked = messageRevokeService.revokeIfNeeded(
        event.getConvId(),
        event.getSeq(),
        RevokeReason.BY_MODERATION,
        ModerationConstants.SYSTEM_OPERATOR_USER_ID);
    if (!revoked) {
      return ModerationResult.ALREADY_REVOKED;
    }
    insertTextLog(event, originalText, match.get());
    return ModerationResult.REVOKED;
  }

  private ModerationResult moderateMedia(MsgSavedEvent event, MediaModerationRequest request) {
    if (hasAnyProcessed(event)) {
      return ModerationResult.DUPLICATE;
    }
    Optional<MediaModerationMatch> match = mediaModerationProvider.scan(request);
    if (match.isEmpty()) {
      return ModerationResult.CLEAN;
    }
    if (hasProcessed(event, match.get().provider())) {
      return ModerationResult.DUPLICATE;
    }
    boolean revoked = messageRevokeService.revokeIfNeeded(
        event.getConvId(),
        event.getSeq(),
        RevokeReason.BY_MODERATION,
        ModerationConstants.SYSTEM_OPERATOR_USER_ID);
    if (!revoked) {
      return ModerationResult.ALREADY_REVOKED;
    }
    insertMediaLog(event, match.get());
    return ModerationResult.REVOKED;
  }

  private boolean hasAnyProcessed(MsgSavedEvent event) {
    return moderationLogMapper.selectFirstByMessage(
        event.getTenantId(),
        event.getServerMsgId()) != null;
  }

  private boolean hasProcessed(MsgSavedEvent event, String provider) {
    return moderationLogMapper.selectByMessageAndProvider(
        event.getTenantId(),
        event.getServerMsgId(),
        provider) != null;
  }

  private void insertTextLog(MsgSavedEvent event, String originalText, ModerationMatch match) {
    ModerationLogEntity log = new ModerationLogEntity();
    log.setTenantId(event.getTenantId());
    log.setMessageId(event.getServerMsgId());
    log.setProvider(ModerationConstants.PROVIDER_DFA);
    log.setCategory(match.category());
    log.setScore(EXACT_MATCH_SCORE);
    log.setActionTaken(ModerationConstants.ACTION_REVOKE);
    log.setOriginalContent(originalText);
    log.setAuditStatus(ModerationConstants.AUDIT_STATUS_AUTO);
    log.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    insertLog(log);
  }

  private void insertMediaLog(MsgSavedEvent event, MediaModerationMatch match) {
    ModerationLogEntity log = new ModerationLogEntity();
    log.setTenantId(event.getTenantId());
    log.setMessageId(event.getServerMsgId());
    log.setProvider(match.provider());
    log.setCategory(match.category());
    log.setScore(match.score());
    log.setActionTaken(ModerationConstants.ACTION_REVOKE);
    log.setOriginalContent(match.evidence());
    log.setAuditStatus(ModerationConstants.AUDIT_STATUS_AUTO);
    log.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    insertLog(log);
  }

  private void insertLog(ModerationLogEntity log) {
    try {
      moderationLogMapper.insert(log);
    } catch (DuplicateKeyException ignored) {
      // MQ 重投或并发消费时，唯一键保证同一消息同一 provider 只留一条审核记录。
    }
  }

  private String mediaType(String mime) {
    if (mime == null || mime.isBlank()) {
      return "file";
    }
    String normalized = mime.toLowerCase(Locale.ROOT);
    if (normalized.startsWith("video/")) {
      return "video";
    }
    if (normalized.startsWith("image/")) {
      return "image";
    }
    if (normalized.startsWith("audio/")) {
      return "voice";
    }
    return "file";
  }

  private String voiceMime(String codec) {
    if (codec == null || codec.isBlank()) {
      return "";
    }
    String normalized = codec.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("audio/")) {
      return normalized;
    }
    return switch (normalized) {
      case "aac" -> "audio/aac";
      case "m4a", "mp4" -> "audio/mp4";
      case "mp3", "mpeg" -> "audio/mpeg";
      case "ogg", "vorbis" -> "audio/ogg";
      case "opus" -> "audio/opus";
      case "wav", "wave" -> "audio/wav";
      default -> "audio/" + normalized;
    };
  }
}
