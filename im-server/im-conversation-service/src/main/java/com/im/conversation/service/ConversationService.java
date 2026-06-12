package com.im.conversation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.conversation.dao.entity.ConversationEntity;
import com.im.conversation.dao.entity.ConversationMemberEntity;
import com.im.conversation.dao.mapper.ConversationMapper;
import com.im.conversation.dao.mapper.ConversationMemberMapper;
import com.im.proto.body.ConvInfo;
import com.im.proto.common.ConvType;
import com.im.proto.rpc.ResolveConvReq;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

  private final ConversationMapper conversationMapper;
  private final ConversationMemberMapper memberMapper;
  private final C2cKeyGenerator c2cKeyGenerator;
  private final ConversationCreator conversationCreator;

  public ConversationService(ConversationMapper conversationMapper,
      ConversationMemberMapper memberMapper,
      C2cKeyGenerator c2cKeyGenerator,
      ConversationCreator conversationCreator) {
    this.conversationMapper = conversationMapper;
    this.memberMapper = memberMapper;
    this.c2cKeyGenerator = c2cKeyGenerator;
    this.conversationCreator = conversationCreator;
  }

  public ConvInfo resolve(ResolveConvReq request) {
    TenantContext.requiredTenantId();
    if (request.getFromUserId() <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "from_user_id must be positive");
    }
    return switch (request.getTargetCase()) {
      case TO_USER_ID -> resolveC2c(request.getFromUserId(), request.getToUserId());
      case CONV_ID -> resolveExistingC2c(request.getFromUserId(), request.getConvId());
      case GROUP_ID -> throw new ImException(ErrorCode.VALIDATION_FAILED, "group conversation is not supported yet");
      case TARGET_NOT_SET -> throw new ImException(ErrorCode.VALIDATION_FAILED, "conversation target is required");
    };
  }

  public List<Long> getMemberUserIds(long conversationId) {
    TenantContext.requiredTenantId();
    if (conversationId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "conv_id must be positive");
    }
    return memberMapper.selectList(Wrappers.lambdaQuery(ConversationMemberEntity.class)
            .eq(ConversationMemberEntity::getConvId, conversationId)
            .orderByAsc(ConversationMemberEntity::getUserId))
        .stream()
        .map(ConversationMemberEntity::getUserId)
        .toList();
  }

  public List<ConvInfo> listMemberConvs(long userId, int limit) {
    TenantContext.requiredTenantId();
    if (userId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "user_id must be positive");
    }
    int effectiveLimit = limit <= 0 ? 100 : Math.min(limit, 100);
    List<ConversationMemberEntity> memberships = memberMapper.selectList(
        Wrappers.lambdaQuery(ConversationMemberEntity.class)
            .eq(ConversationMemberEntity::getUserId, userId)
            .isNull(ConversationMemberEntity::getDeletedAt)
            .orderByDesc(ConversationMemberEntity::getCreatedAt)
            .last("LIMIT " + effectiveLimit));
    return memberships.stream()
        .map(member -> toMemberConvInfo(userId, member))
        .toList();
  }

  private ConvInfo resolveC2c(long fromUserId, long toUserId) {
    String c2cKey = c2cKeyGenerator.generate(fromUserId, toUserId);
    ConversationEntity existing = findByC2cKey(c2cKey);
    if (existing != null) {
      return toC2cConvInfo(existing, fromUserId, toUserId);
    }

    try {
      ConversationEntity created = conversationCreator.createC2c(c2cKey, fromUserId, toUserId);
      return toC2cConvInfo(created, fromUserId, toUserId);
    } catch (DuplicateKeyException ex) {
      ConversationEntity createdByPeer = findByC2cKey(c2cKey);
      if (createdByPeer == null) {
        throw new ImException(ErrorCode.INTERNAL_ERROR, "duplicated c2c conversation but row not found", ex);
      }
      return toC2cConvInfo(createdByPeer, fromUserId, toUserId);
    }
  }

  private ConvInfo resolveExistingC2c(long fromUserId, long conversationId) {
    if (conversationId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "conv_id must be positive");
    }
    ConversationEntity conversation = conversationMapper.selectById(conversationId);
    if (conversation == null) {
      throw new ImException(ErrorCode.CONV_NOT_FOUND);
    }
    if (!Integer.valueOf(ConvType.C2C.getNumber()).equals(conversation.getType())) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "only c2c conversation is supported");
    }

    ConversationMemberEntity currentMember = findMember(conversationId, fromUserId);
    if (currentMember == null) {
      throw new ImException(ErrorCode.NOT_CONV_MEMBER);
    }
    long peerUserId = findMembers(conversationId).stream()
        .map(ConversationMemberEntity::getUserId)
        .filter(userId -> userId != fromUserId)
        .findFirst()
        .orElseThrow(() -> new ImException(ErrorCode.INTERNAL_ERROR, "c2c peer member not found"));
    return toConvInfo(conversation, peerUserId, currentMember);
  }

  private ConversationEntity findByC2cKey(String c2cKey) {
    return conversationMapper.selectOne(Wrappers.lambdaQuery(ConversationEntity.class)
        .eq(ConversationEntity::getC2cKey, c2cKey));
  }

  private ConversationMemberEntity findMember(long conversationId, long userId) {
    return memberMapper.selectOne(Wrappers.lambdaQuery(ConversationMemberEntity.class)
        .eq(ConversationMemberEntity::getConvId, conversationId)
        .eq(ConversationMemberEntity::getUserId, userId));
  }

  private List<ConversationMemberEntity> findMembers(long conversationId) {
    return memberMapper.selectList(Wrappers.lambdaQuery(ConversationMemberEntity.class)
        .eq(ConversationMemberEntity::getConvId, conversationId)
        .orderByAsc(ConversationMemberEntity::getUserId));
  }

  private ConvInfo toC2cConvInfo(ConversationEntity conversation, long fromUserId, long peerUserId) {
    ConversationMemberEntity member = findMember(conversation.getId(), fromUserId);
    if (member == null) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "conversation member not found");
    }
    return toConvInfo(conversation, peerUserId, member);
  }

  private ConvInfo toMemberConvInfo(long userId, ConversationMemberEntity member) {
    ConversationEntity conversation = conversationMapper.selectById(member.getConvId());
    if (conversation == null) {
      throw new ImException(ErrorCode.CONV_NOT_FOUND);
    }
    long peerUserId = 0L;
    if (Integer.valueOf(ConvType.C2C.getNumber()).equals(conversation.getType())) {
      peerUserId = findMembers(conversation.getId()).stream()
          .map(ConversationMemberEntity::getUserId)
          .filter(memberUserId -> memberUserId != userId)
          .findFirst()
          .orElse(0L);
    }
    return toConvInfo(conversation, peerUserId, member);
  }

  private ConvInfo toConvInfo(ConversationEntity conversation, long peerUserId,
      ConversationMemberEntity member) {
    ConvType type = convType(conversation);
    ConvInfo.Builder builder = ConvInfo.newBuilder()
        .setConvId(conversation.getId())
        .setType(type)
        .setTitle(defaultTitle(type, peerUserId))
        .setPeerUserId(peerUserId)
        .setGroupId(nullToZero(conversation.getGroupId()))
        .setMaxSeq(nullToZero(conversation.getMaxSeq()))
        .setReadSeq(nullToZero(member.getReadSeq()))
        .setPinned(toBool(member.getPinned()))
        .setMuted(toBool(member.getMuted()))
        .setLastMsgAbstract(nullToBlank(conversation.getLastMsgAbstract()));
    if (conversation.getLastMsgTime() != null) {
      builder.setLastMsgTime(conversation.getLastMsgTime().toInstant(ZoneOffset.UTC).toEpochMilli());
    }
    return builder.build();
  }

  private ConvType convType(ConversationEntity conversation) {
    ConvType type = ConvType.forNumber(conversation.getType() == null ? 0 : conversation.getType());
    return type == null ? ConvType.CONV_TYPE_UNSPECIFIED : type;
  }

  private String defaultTitle(ConvType type, long peerUserId) {
    if (type == ConvType.C2C && peerUserId > 0) {
      return Long.toString(peerUserId);
    }
    return "";
  }

  private long nullToZero(Long value) {
    return value == null ? 0L : value;
  }

  private boolean toBool(Integer value) {
    return value != null && value != 0;
  }

  private String nullToBlank(String value) {
    return value == null ? "" : value;
  }
}
