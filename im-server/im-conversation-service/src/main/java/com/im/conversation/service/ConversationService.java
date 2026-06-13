package com.im.conversation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.im.common.conversation.UserConvEventType;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.conversation.dao.entity.ConversationEntity;
import com.im.conversation.dao.entity.ConversationMemberEntity;
import com.im.conversation.dao.entity.GroupInfoEntity;
import com.im.conversation.dao.entity.UserConvEventEntity;
import com.im.conversation.dao.mapper.ConversationGroupInfoMapper;
import com.im.conversation.dao.mapper.ConversationMapper;
import com.im.conversation.dao.mapper.ConversationMemberMapper;
import com.im.conversation.dao.mapper.ConversationUserConvEventMapper;
import com.im.conversation.dao.mapper.ConversationUserConvVersionMapper;
import com.im.proto.body.ConvInfo;
import com.im.proto.common.ConvType;
import com.im.proto.rpc.ResolveConvReq;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

  private final ConversationMapper conversationMapper;
  private final ConversationMemberMapper memberMapper;
  private final ConversationGroupInfoMapper groupInfoMapper;
  private final ConversationUserConvVersionMapper userConvVersionMapper;
  private final ConversationUserConvEventMapper userConvEventMapper;
  private final C2cKeyGenerator c2cKeyGenerator;
  private final ConversationCreator conversationCreator;

  public ConversationService(ConversationMapper conversationMapper,
      ConversationMemberMapper memberMapper,
      ConversationGroupInfoMapper groupInfoMapper,
      ConversationUserConvVersionMapper userConvVersionMapper,
      ConversationUserConvEventMapper userConvEventMapper,
      C2cKeyGenerator c2cKeyGenerator,
      ConversationCreator conversationCreator) {
    this.conversationMapper = conversationMapper;
    this.memberMapper = memberMapper;
    this.groupInfoMapper = groupInfoMapper;
    this.userConvVersionMapper = userConvVersionMapper;
    this.userConvEventMapper = userConvEventMapper;
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
      case CONV_ID -> resolveExisting(request.getFromUserId(), request.getConvId());
      case GROUP_ID -> resolveGroup(request.getFromUserId(), request.getGroupId());
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
            .isNull(ConversationMemberEntity::getDeletedAt)
            .orderByAsc(ConversationMemberEntity::getUserId))
        .stream()
        .map(ConversationMemberEntity::getUserId)
        .toList();
  }

  public ConversationListResult listMemberConvs(long userId, int limit, long afterVersion) {
    long tenantId = TenantContext.requiredTenantId();
    if (userId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "user_id must be positive");
    }
    int effectiveLimit = limit <= 0 ? 100 : Math.min(limit, 100);
    long currentVersion = currentConvListVersion(tenantId, userId);
    if (afterVersion <= 0) {
      return new ConversationListResult(listActiveMemberConvs(userId, effectiveLimit),
          false, currentVersion);
    }
    if (afterVersion >= currentVersion) {
      return new ConversationListResult(List.of(), false, currentVersion);
    }
    List<UserConvEventEntity> events = userConvEventMapper.selectAfterVersion(
        userId, afterVersion, effectiveLimit + 1);
    if (events.size() > effectiveLimit) {
      return new ConversationListResult(listActiveMemberConvs(userId, effectiveLimit),
          true, currentVersion);
    }
    Map<Long, UserConvEventEntity> latestByConversation = new LinkedHashMap<>();
    for (UserConvEventEntity event : events) {
      latestByConversation.put(event.getConvId(), event);
    }
    List<ConvInfo> convs = latestByConversation.values().stream()
        .map(event -> toEventConvInfo(userId, event))
        .toList();
    return new ConversationListResult(convs, false, currentVersion);
  }

  private List<ConvInfo> listActiveMemberConvs(long userId, int effectiveLimit) {
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

  public ConvInfo getMemberConv(long userId, long conversationId) {
    TenantContext.requiredTenantId();
    if (userId <= 0 || conversationId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED);
    }
    ConversationMemberEntity member = findMember(conversationId, userId);
    if (member == null) {
      throw new ImException(ErrorCode.NOT_CONV_MEMBER);
    }
    return toMemberConvInfo(userId, member);
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

  private ConvInfo resolveExisting(long fromUserId, long conversationId) {
    if (conversationId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "conv_id must be positive");
    }
    ConversationEntity conversation = conversationMapper.selectById(conversationId);
    if (conversation == null) {
      throw new ImException(ErrorCode.CONV_NOT_FOUND);
    }
    ConvType type = convType(conversation);
    if (type == ConvType.C2C) {
      return resolveExistingC2c(fromUserId, conversation);
    }
    if (type == ConvType.GROUP) {
      return resolveExistingGroup(fromUserId, conversation);
    }
    throw new ImException(ErrorCode.VALIDATION_FAILED, "unsupported conversation type");
  }

  private ConvInfo resolveExistingC2c(long fromUserId, ConversationEntity conversation) {
    ConversationMemberEntity currentMember = findMember(conversation.getId(), fromUserId);
    if (currentMember == null) {
      throw new ImException(ErrorCode.NOT_CONV_MEMBER);
    }
    long peerUserId = findMembers(conversation.getId()).stream()
        .map(ConversationMemberEntity::getUserId)
        .filter(userId -> userId != fromUserId)
        .findFirst()
        .orElseThrow(() -> new ImException(ErrorCode.INTERNAL_ERROR, "c2c peer member not found"));
    return toConvInfo(conversation, peerUserId, currentMember);
  }

  private ConvInfo resolveGroup(long fromUserId, long groupId) {
    if (groupId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "group_id must be positive");
    }
    ConversationEntity conversation = conversationMapper.selectOne(Wrappers
        .lambdaQuery(ConversationEntity.class)
        .eq(ConversationEntity::getType, ConvType.GROUP.getNumber())
        .eq(ConversationEntity::getGroupId, groupId));
    if (conversation == null) {
      throw new ImException(ErrorCode.GROUP_NOT_FOUND);
    }
    return resolveExistingGroup(fromUserId, conversation);
  }

  private ConvInfo resolveExistingGroup(long fromUserId, ConversationEntity conversation) {
    ConversationMemberEntity currentMember = findMember(conversation.getId(), fromUserId);
    if (currentMember == null) {
      throw new ImException(ErrorCode.NOT_GROUP_MEMBER);
    }
    return toConvInfo(conversation, 0L, currentMember);
  }

  private ConversationEntity findByC2cKey(String c2cKey) {
    return conversationMapper.selectOne(Wrappers.lambdaQuery(ConversationEntity.class)
        .eq(ConversationEntity::getC2cKey, c2cKey));
  }

  private ConversationMemberEntity findMember(long conversationId, long userId) {
    return memberMapper.selectOne(Wrappers.lambdaQuery(ConversationMemberEntity.class)
        .eq(ConversationMemberEntity::getConvId, conversationId)
        .eq(ConversationMemberEntity::getUserId, userId)
        .isNull(ConversationMemberEntity::getDeletedAt));
  }

  private List<ConversationMemberEntity> findMembers(long conversationId) {
    return memberMapper.selectList(Wrappers.lambdaQuery(ConversationMemberEntity.class)
        .eq(ConversationMemberEntity::getConvId, conversationId)
        .isNull(ConversationMemberEntity::getDeletedAt)
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

  private ConvInfo toEventConvInfo(long userId, UserConvEventEntity event) {
    if (UserConvEventType.REMOVED.value().equals(event.getEventType())) {
      return removedConvInfo(event.getConvId());
    }
    ConversationMemberEntity member = findMember(event.getConvId(), userId);
    if (member == null) {
      return removedConvInfo(event.getConvId());
    }
    return toMemberConvInfo(userId, member);
  }

  private ConvInfo removedConvInfo(long conversationId) {
    ConversationEntity conversation = conversationMapper.selectById(conversationId);
    ConvInfo.Builder builder = ConvInfo.newBuilder()
        .setConvId(conversationId)
        .setDeleted(true);
    if (conversation == null) {
      return builder.build();
    }
    ConvType type = convType(conversation);
    builder.setType(type)
        .setGroupId(nullToZero(conversation.getGroupId()))
        .setMaxSeq(nullToZero(conversation.getMaxSeq()))
        .setLastMsgAbstract(nullToBlank(conversation.getLastMsgAbstract()));
    if (conversation.getLastMsgTime() != null) {
      builder.setLastMsgTime(conversation.getLastMsgTime().toInstant(ZoneOffset.UTC).toEpochMilli());
    }
    return builder.build();
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
    if (type == ConvType.GROUP) {
      applyGroupInfo(builder, conversation);
    }
    return builder.build();
  }

  private void applyGroupInfo(ConvInfo.Builder builder, ConversationEntity conversation) {
    Long groupId = conversation.getGroupId();
    if (groupId == null || groupId <= 0) {
      throw new ImException(ErrorCode.GROUP_NOT_FOUND);
    }
    GroupInfoEntity group = groupInfoMapper.selectById(groupId);
    if (group == null) {
      throw new ImException(ErrorCode.GROUP_NOT_FOUND);
    }
    builder.setTitle(nullToBlank(group.getName()))
        .setAvatar(nullToBlank(group.getAvatar()));
  }

  private ConvType convType(ConversationEntity conversation) {
    ConvType type = ConvType.forNumber(conversation.getType() == null ? 0 : conversation.getType());
    return type == null ? ConvType.CONV_TYPE_UNSPECIFIED : type;
  }

  private String defaultTitle(ConvType type, long peerUserId) {
    if (type == ConvType.C2C && peerUserId > 0) {
      return Long.toString(peerUserId);
    }
    if (type == ConvType.GROUP) {
      return "";
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

  private long currentConvListVersion(long tenantId, long userId) {
    Long version = userConvVersionMapper.selectVersion(tenantId, userId);
    return version == null ? 0L : version;
  }
}
