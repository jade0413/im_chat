package com.im.conversation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.conversation.dao.entity.ConversationEntity;
import com.im.conversation.dao.entity.ConversationMemberEntity;
import com.im.conversation.dao.mapper.ConversationMapper;
import com.im.conversation.dao.mapper.ConversationMemberMapper;
import com.im.proto.body.ReadNotify;
import com.im.proto.body.ReadReport;
import com.im.proto.common.ConvType;
import com.im.proto.rpc.ConnCtx;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReadReceiptService {

  private final ConversationMapper conversationMapper;
  private final ConversationMemberMapper memberMapper;
  private final ReadReceiptPusher readReceiptPusher;

  public ReadReceiptService(ConversationMapper conversationMapper,
      ConversationMemberMapper memberMapper,
      ReadReceiptPusher readReceiptPusher) {
    this.conversationMapper = conversationMapper;
    this.memberMapper = memberMapper;
    this.readReceiptPusher = readReceiptPusher;
  }

  public ReadReceiptResult reportRead(ConnCtx ctx, ReadReport request) {
    TenantContext.requiredTenantId();
    validate(ctx, request);
    ConversationEntity conversation = conversationMapper.selectById(request.getConvId());
    if (conversation == null) {
      throw new ImException(ErrorCode.CONV_NOT_FOUND);
    }
    long maxSeq = nullToZero(conversation.getMaxSeq());
    if (request.getReadSeq() > maxSeq) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "read_seq cannot exceed conversation max_seq");
    }

    ConversationMemberEntity member = findMember(request.getConvId(), ctx.getUserId());
    if (member == null) {
      throw new ImException(ErrorCode.NOT_CONV_MEMBER);
    }
    long currentReadSeq = nullToZero(member.getReadSeq());
    boolean changed = false;
    long effectiveReadSeq = currentReadSeq;
    if (request.getReadSeq() > currentReadSeq) {
      ConversationMemberEntity update = new ConversationMemberEntity();
      update.setReadSeq(request.getReadSeq());
      int updated = memberMapper.update(update, Wrappers.lambdaUpdate(ConversationMemberEntity.class)
          .eq(ConversationMemberEntity::getConvId, request.getConvId())
          .eq(ConversationMemberEntity::getUserId, ctx.getUserId())
          .isNull(ConversationMemberEntity::getDeletedAt)
          .lt(ConversationMemberEntity::getReadSeq, request.getReadSeq()));
      ConversationMemberEntity latest = findMember(request.getConvId(), ctx.getUserId());
      effectiveReadSeq = latest == null ? request.getReadSeq() : nullToZero(latest.getReadSeq());
      changed = updated > 0 && effectiveReadSeq == request.getReadSeq();
    }

    ReadNotify notify = ReadNotify.newBuilder()
        .setConvId(request.getConvId())
        .setReaderUserId(ctx.getUserId())
        .setReadSeq(effectiveReadSeq)
        .build();
    if (changed) {
      readReceiptPusher.pushReadNotify(ctx, notifyTargetUserIds(ctx, conversation), notify);
    }
    return new ReadReceiptResult(notify, changed);
  }

  private void validate(ConnCtx ctx, ReadReport request) {
    if (ctx == null || ctx.getUserId() <= 0 || ctx.getConnId().isBlank()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "valid connection context is required");
    }
    if (request.getConvId() <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "conv_id must be positive");
    }
    if (request.getReadSeq() < 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "read_seq must not be negative");
    }
  }

  private ConversationMemberEntity findMember(long conversationId, long userId) {
    return memberMapper.selectOne(Wrappers.lambdaQuery(ConversationMemberEntity.class)
        .eq(ConversationMemberEntity::getConvId, conversationId)
        .eq(ConversationMemberEntity::getUserId, userId)
        .isNull(ConversationMemberEntity::getDeletedAt));
  }

  private List<Long> getMemberUserIds(long conversationId) {
    return memberMapper.selectList(Wrappers.lambdaQuery(ConversationMemberEntity.class)
            .eq(ConversationMemberEntity::getConvId, conversationId)
            .isNull(ConversationMemberEntity::getDeletedAt)
            .orderByAsc(ConversationMemberEntity::getUserId))
        .stream()
        .map(ConversationMemberEntity::getUserId)
        .toList();
  }

  private List<Long> notifyTargetUserIds(ConnCtx ctx, ConversationEntity conversation) {
    if (ConvType.GROUP == convType(conversation)) {
      return List.of(ctx.getUserId());
    }
    return getMemberUserIds(conversation.getId());
  }

  private ConvType convType(ConversationEntity conversation) {
    Integer type = conversation.getType();
    ConvType convType = type == null ? ConvType.CONV_TYPE_UNSPECIFIED : ConvType.forNumber(type);
    return convType == null ? ConvType.CONV_TYPE_UNSPECIFIED : convType;
  }

  private long nullToZero(Long value) {
    return value == null ? 0L : value;
  }
}
