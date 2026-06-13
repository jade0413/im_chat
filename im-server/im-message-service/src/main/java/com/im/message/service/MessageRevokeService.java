package com.im.message.service;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.outbox.OutboxWriter;
import com.im.common.tenant.TenantContext;
import com.im.message.dao.entity.MessageEntity;
import com.im.message.dao.mapper.ConversationProgressMapper;
import com.im.message.dao.mapper.MessageMapper;
import com.im.proto.common.RevokeReason;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageRevokeService {

  private static final Duration SENDER_REVOKE_WINDOW = Duration.ofMinutes(2);
  private static final String REVOKED_ABSTRACT = "message revoked";

  private final MessageMapper messageMapper;
  private final ConversationProgressMapper conversationProgressMapper;
  private final ConversationMemberClient memberClient;
  private final OutboxWriter outboxWriter;
  private final MsgRevokedEventFactory eventFactory;
  private final Clock clock;

  @Autowired
  public MessageRevokeService(MessageMapper messageMapper,
      ConversationProgressMapper conversationProgressMapper,
      ConversationMemberClient memberClient,
      OutboxWriter outboxWriter,
      MsgRevokedEventFactory eventFactory) {
    this(messageMapper, conversationProgressMapper, memberClient, outboxWriter, eventFactory,
        Clock.systemUTC());
  }

  MessageRevokeService(MessageMapper messageMapper,
      ConversationProgressMapper conversationProgressMapper,
      ConversationMemberClient memberClient,
      OutboxWriter outboxWriter,
      MsgRevokedEventFactory eventFactory,
      Clock clock) {
    this.messageMapper = messageMapper;
    this.conversationProgressMapper = conversationProgressMapper;
    this.memberClient = memberClient;
    this.outboxWriter = outboxWriter;
    this.eventFactory = eventFactory;
    this.clock = clock;
  }

  @Transactional
  public void revoke(long conversationId, long seq, RevokeReason reason, long operatorUserId) {
    long tenantId = TenantContext.requiredTenantId();
    validateRequest(conversationId, seq, reason, operatorUserId);
    MessageEntity message = messageMapper.selectByConversationSeq(tenantId, conversationId, seq);
    if (message == null) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "message not found");
    }
    if (isRevoked(message)) {
      return;
    }
    validatePermission(message, reason, operatorUserId);
    int updated = messageMapper.markRevoked(
        tenantId,
        conversationId,
        seq,
        MessageAssembler.STATUS_REVOKED,
        reason.getNumber());
    if (updated == 0) {
      return;
    }
    conversationProgressMapper.updateLastMessageAbstractIfLatest(
        tenantId, conversationId, seq, REVOKED_ABSTRACT);
    MsgRevokedEventFactory.MsgRevokedOutboxEvent event = eventFactory.create(
        tenantId,
        conversationId,
        seq,
        message.getId(),
        reason.getNumber(),
        operatorUserId);
    outboxWriter.write(tenantId, event.eventType(), event.routingKey(), event.payload());
  }

  private void validateRequest(long conversationId, long seq, RevokeReason reason,
      long operatorUserId) {
    if (conversationId <= 0 || seq <= 0 || operatorUserId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED);
    }
    if (reason == null || reason == RevokeReason.REVOKE_REASON_UNSPECIFIED) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "revoke reason is required");
    }
  }

  private void validatePermission(MessageEntity message, RevokeReason reason, long operatorUserId) {
    if (reason == RevokeReason.BY_SENDER) {
      memberClient.getMemberConv(operatorUserId, message.getConversationId());
      if (!Long.valueOf(operatorUserId).equals(message.getSenderId())) {
        throw new ImException(ErrorCode.NO_PERMISSION);
      }
      if (outsideSenderWindow(message.getCreatedAt())) {
        throw new ImException(ErrorCode.REVOKE_WINDOW_EXPIRED);
      }
    }
  }

  private boolean outsideSenderWindow(LocalDateTime createdAt) {
    if (createdAt == null) {
      return false;
    }
    return Duration.between(createdAt.toInstant(ZoneOffset.UTC), clock.instant())
        .compareTo(SENDER_REVOKE_WINDOW) > 0;
  }

  private boolean isRevoked(MessageEntity message) {
    return Integer.valueOf(MessageAssembler.STATUS_REVOKED).equals(message.getStatus());
  }
}
