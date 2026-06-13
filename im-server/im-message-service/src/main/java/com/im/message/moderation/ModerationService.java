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
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationService {

  private static final BigDecimal EXACT_MATCH_SCORE = BigDecimal.ONE.setScale(4);

  private final SensitiveWordService sensitiveWordService;
  private final ModerationLogMapper moderationLogMapper;
  private final MessageRevokeService messageRevokeService;
  private final ModerationProperties properties;
  private final Clock clock;

  @Autowired
  public ModerationService(SensitiveWordService sensitiveWordService,
      ModerationLogMapper moderationLogMapper,
      MessageRevokeService messageRevokeService,
      ModerationProperties properties) {
    this(sensitiveWordService, moderationLogMapper, messageRevokeService, properties,
        Clock.systemUTC());
  }

  ModerationService(SensitiveWordService sensitiveWordService,
      ModerationLogMapper moderationLogMapper,
      MessageRevokeService messageRevokeService,
      ModerationProperties properties,
      Clock clock) {
    this.sensitiveWordService = sensitiveWordService;
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
    if (content.getContentCase() != MsgContent.ContentCase.TEXT) {
      return ModerationResult.SKIPPED;
    }
    if (hasProcessed(event)) {
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
    insertLog(event, originalText, match.get());
    return ModerationResult.REVOKED;
  }

  private boolean hasProcessed(MsgSavedEvent event) {
    return moderationLogMapper.selectByMessageAndProvider(
        event.getTenantId(),
        event.getServerMsgId(),
        ModerationConstants.PROVIDER_DFA) != null;
  }

  private void insertLog(MsgSavedEvent event, String originalText, ModerationMatch match) {
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
    try {
      moderationLogMapper.insert(log);
    } catch (DuplicateKeyException ignored) {
      // MQ 重投或并发消费时，唯一键保证同一消息同一 provider 只留一条审核记录。
    }
  }
}
