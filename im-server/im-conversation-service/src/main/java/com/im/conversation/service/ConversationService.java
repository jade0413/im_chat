package com.im.conversation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.im.common.conversation.ConversationMemberCache;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  private final ConversationMemberCache memberCache;

  public ConversationService(ConversationMapper conversationMapper,
      ConversationMemberMapper memberMapper,
      ConversationGroupInfoMapper groupInfoMapper,
      ConversationUserConvVersionMapper userConvVersionMapper,
      ConversationUserConvEventMapper userConvEventMapper,
      C2cKeyGenerator c2cKeyGenerator,
      ConversationCreator conversationCreator,
      ConversationMemberCache memberCache) {
    this.conversationMapper = conversationMapper;
    this.memberMapper = memberMapper;
    this.groupInfoMapper = groupInfoMapper;
    this.userConvVersionMapper = userConvVersionMapper;
    this.userConvEventMapper = userConvEventMapper;
    this.c2cKeyGenerator = c2cKeyGenerator;
    this.conversationCreator = conversationCreator;
    this.memberCache = memberCache;
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
    long tenantId = TenantContext.requiredTenantId();
    if (conversationId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "conv_id must be positive");
    }
    return cachedMemberUserIds(tenantId, conversationId);
  }

  /** 成员 userId 列表：优先读缓存，未命中回源并回填（消息扇出热路径）。 */
  private List<Long> cachedMemberUserIds(long tenantId, long conversationId) {
    Optional<List<Long>> cached = memberCache.get(tenantId, conversationId);
    if (cached.isPresent()) {
      return cached.get();
    }
    List<Long> userIds = queryMemberUserIds(conversationId);
    memberCache.put(tenantId, conversationId, userIds);
    return userIds;
  }

  private List<Long> queryMemberUserIds(long conversationId) {
    return memberMapper.selectList(Wrappers.lambdaQuery(ConversationMemberEntity.class)
            .eq(ConversationMemberEntity::getConvId, conversationId)
            .isNull(ConversationMemberEntity::getDeletedAt)
            .orderByAsc(ConversationMemberEntity::getUserId))
        .stream()
        .map(ConversationMemberEntity::getUserId)
        .toList();
  }

  /**
   * 返回会话成员列表以及 CS 推送路由所需元数据（D33）。
   * 非 CS 会话的 csStatus/agentId 为 0。
   */
  public MembersResult getMembersResult(long conversationId) {
    long tenantId = TenantContext.requiredTenantId();
    if (conversationId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "conv_id must be positive");
    }
    // 会话行（type/cs_status/agent_id 会频繁变动）始终实时读，保证 CS 路由新鲜
    ConversationEntity conv = conversationMapper.selectById(conversationId);
    int convType = conv != null && conv.getType() != null ? conv.getType() : 0;
    int csStatus = (conv != null && conv.getCsStatus() != null) ? conv.getCsStatus() : 0;
    long agentId  = (conv != null && conv.getAgentId() != null)  ? conv.getAgentId()  : 0L;

    // 成员列表变动很少，走缓存（变更处显式失效 + 60s 兜底 TTL）
    List<Long> userIds = cachedMemberUserIds(tenantId, conversationId);
    return new MembersResult(userIds, convType, csStatus, agentId);
  }

  /** 会话成员列表及 CS 路由元数据（GetMembers gRPC 响应的 Java 侧）。 */
  public record MembersResult(List<Long> userIds, int convType, int csStatus, long agentId) {}

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
        tenantId, userId, afterVersion, effectiveLimit + 1);
    if (events.size() > effectiveLimit) {
      return new ConversationListResult(listActiveMemberConvs(userId, effectiveLimit),
          true, currentVersion);
    }
    Map<Long, UserConvEventEntity> latestByConversation = new LinkedHashMap<>();
    for (UserConvEventEntity event : events) {
      latestByConversation.put(event.getConvId(), event);
    }
    List<ConvInfo> convs = buildEventConvInfos(userId, latestByConversation.values());
    return new ConversationListResult(convs, false, currentVersion);
  }

  private List<ConvInfo> listActiveMemberConvs(long userId, int effectiveLimit) {
    List<ConversationMemberEntity> memberships = memberMapper.selectList(
        Wrappers.lambdaQuery(ConversationMemberEntity.class)
            .eq(ConversationMemberEntity::getUserId, userId)
            .isNull(ConversationMemberEntity::getDeletedAt)
            .orderByDesc(ConversationMemberEntity::getCreatedAt)
            .last("LIMIT " + effectiveLimit));
    return buildConvInfos(userId, memberships);
  }

  /**
   * 批量构建会话列表 ConvInfo，消除 N+1：
   * 一次性批量取会话行、C2C 对端、群信息，避免逐会话查库（SYNC/重连热路径）。
   */
  private List<ConvInfo> buildConvInfos(long userId, List<ConversationMemberEntity> memberships) {
    if (memberships.isEmpty()) {
      return List.of();
    }
    List<Long> convIds = memberships.stream()
        .map(ConversationMemberEntity::getConvId)
        .distinct()
        .toList();
    Map<Long, ConversationEntity> convById = batchLoadConversations(convIds);
    Map<Long, Long> peerByConv = batchLoadC2cPeers(userId, convById);
    Map<Long, GroupInfoEntity> groupById = batchLoadGroups(convById);

    List<ConvInfo> result = new ArrayList<>(memberships.size());
    for (ConversationMemberEntity member : memberships) {
      ConversationEntity conv = convById.get(member.getConvId());
      if (conv == null) {
        continue;
      }
      ConvType type = convType(conv);
      GroupInfoEntity group = null;
      if (type == ConvType.GROUP) {
        group = groupById.get(nullToZero(conv.getGroupId()));
        if (group == null) {
          // 群信息缺失则跳过该会话，避免整张列表因单条脏数据失败
          continue;
        }
      }
      long peerUserId = type == ConvType.C2C ? peerByConv.getOrDefault(conv.getId(), 0L) : 0L;
      result.add(buildConvInfo(conv, peerUserId, member, group));
    }
    return result;
  }

  /** 增量事件 → ConvInfo（批量），保持事件顺序；缺失会话/成员按已删除处理。 */
  private List<ConvInfo> buildEventConvInfos(long userId, Collection<UserConvEventEntity> events) {
    List<Long> activeConvIds = events.stream()
        .filter(event -> !UserConvEventType.REMOVED.value().equals(event.getEventType()))
        .map(UserConvEventEntity::getConvId)
        .distinct()
        .toList();
    Map<Long, ConvInfo> activeByConv = new HashMap<>();
    if (!activeConvIds.isEmpty()) {
      List<ConversationMemberEntity> members = memberMapper.selectList(
          Wrappers.lambdaQuery(ConversationMemberEntity.class)
              .eq(ConversationMemberEntity::getUserId, userId)
              .in(ConversationMemberEntity::getConvId, activeConvIds)
              .isNull(ConversationMemberEntity::getDeletedAt));
      for (ConvInfo info : buildConvInfos(userId, members)) {
        activeByConv.put(info.getConvId(), info);
      }
    }
    List<ConvInfo> result = new ArrayList<>(events.size());
    for (UserConvEventEntity event : events) {
      if (UserConvEventType.REMOVED.value().equals(event.getEventType())) {
        result.add(removedConvInfo(event.getConvId()));
        continue;
      }
      ConvInfo info = activeByConv.get(event.getConvId());
      result.add(info != null ? info : removedConvInfo(event.getConvId()));
    }
    return result;
  }

  private Map<Long, ConversationEntity> batchLoadConversations(List<Long> convIds) {
    if (convIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, ConversationEntity> map = new HashMap<>();
    for (ConversationEntity conversation : conversationMapper.selectBatchIds(convIds)) {
      map.put(conversation.getId(), conversation);
    }
    return map;
  }

  private Map<Long, Long> batchLoadC2cPeers(long userId, Map<Long, ConversationEntity> convById) {
    List<Long> c2cConvIds = convById.values().stream()
        .filter(conv -> Integer.valueOf(ConvType.C2C.getNumber()).equals(conv.getType()))
        .map(ConversationEntity::getId)
        .toList();
    if (c2cConvIds.isEmpty()) {
      return Map.of();
    }
    List<ConversationMemberEntity> members = memberMapper.selectList(
        Wrappers.lambdaQuery(ConversationMemberEntity.class)
            .in(ConversationMemberEntity::getConvId, c2cConvIds)
            .isNull(ConversationMemberEntity::getDeletedAt));
    Map<Long, Long> peerByConv = new HashMap<>();
    for (ConversationMemberEntity member : members) {
      if (member.getUserId() != null && member.getUserId() != userId) {
        peerByConv.putIfAbsent(member.getConvId(), member.getUserId());
      }
    }
    return peerByConv;
  }

  private Map<Long, GroupInfoEntity> batchLoadGroups(Map<Long, ConversationEntity> convById) {
    List<Long> groupIds = convById.values().stream()
        .filter(conv -> Integer.valueOf(ConvType.GROUP.getNumber()).equals(conv.getType()))
        .map(ConversationEntity::getGroupId)
        .filter(groupId -> groupId != null && groupId > 0)
        .distinct()
        .toList();
    if (groupIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, GroupInfoEntity> map = new HashMap<>();
    for (GroupInfoEntity group : groupInfoMapper.selectBatchIds(groupIds)) {
      map.put(group.getId(), group);
    }
    return map;
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

  /** 找或建用户 SYSTEM 通知会话（D40）。借道 c2c_key="sys:"+userId 去重，与 resolveC2c 同并发模式。 */
  public ConvInfo resolveSystemConv(long userId) {
    TenantContext.requiredTenantId();
    if (userId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "user_id must be positive");
    }
    String systemKey = "sys:" + userId;
    ConversationEntity existing = findByC2cKey(systemKey);
    if (existing != null) {
      return toSystemConvInfo(existing, userId);
    }
    try {
      ConversationEntity created = conversationCreator.createSystem(systemKey, userId);
      return toSystemConvInfo(created, userId);
    } catch (DuplicateKeyException ex) {
      ConversationEntity createdByRace = findByC2cKey(systemKey);
      if (createdByRace == null) {
        throw new ImException(ErrorCode.INTERNAL_ERROR, "duplicated system conversation but row not found", ex);
      }
      return toSystemConvInfo(createdByRace, userId);
    }
  }

  private ConvInfo toSystemConvInfo(ConversationEntity conversation, long userId) {
    ConversationMemberEntity member = findMember(conversation.getId(), userId);
    if (member == null) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "system conversation member not found");
    }
    return toConvInfo(conversation, 0L, member);
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
    if (type == ConvType.CS_SESSION) {
      return resolveExistingCs(fromUserId, conversation);
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

  private ConvInfo resolveExistingCs(long fromUserId, ConversationEntity conversation) {
    if (Integer.valueOf(3).equals(conversation.getCsStatus())) {
      throw new ImException(ErrorCode.NO_PERMISSION, "CS 会话已结单");
    }
    ConversationMemberEntity currentMember = findMember(conversation.getId(), fromUserId);
    if (currentMember == null) {
      throw new ImException(ErrorCode.NOT_CONV_MEMBER);
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

  /** 单条路径：按需查群信息后委托批量构建器（保持与列表路径一致的装配逻辑）。 */
  private ConvInfo toConvInfo(ConversationEntity conversation, long peerUserId,
      ConversationMemberEntity member) {
    GroupInfoEntity group = convType(conversation) == ConvType.GROUP
        ? loadGroupInfo(conversation.getGroupId())
        : null;
    return buildConvInfo(conversation, peerUserId, member, group);
  }

  /** 不查库的 ConvInfo 装配：群信息由调用方预取传入。 */
  private ConvInfo buildConvInfo(ConversationEntity conversation, long peerUserId,
      ConversationMemberEntity member, GroupInfoEntity group) {
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
      if (group == null) {
        throw new ImException(ErrorCode.GROUP_NOT_FOUND);
      }
      builder.setTitle(nullToBlank(group.getName()))
          .setAvatar(nullToBlank(group.getAvatar()));
    }
    if (type == ConvType.CS_SESSION && conversation.getCsStatus() != null) {
      builder.setCsStatus(Integer.toString(conversation.getCsStatus()));
    }
    return builder.build();
  }

  private GroupInfoEntity loadGroupInfo(Long groupId) {
    if (groupId == null || groupId <= 0) {
      throw new ImException(ErrorCode.GROUP_NOT_FOUND);
    }
    GroupInfoEntity group = groupInfoMapper.selectById(groupId);
    if (group == null) {
      throw new ImException(ErrorCode.GROUP_NOT_FOUND);
    }
    return group;
  }

  private ConvType convType(ConversationEntity conversation) {
    ConvType type = ConvType.forNumber(conversation.getType() == null ? 0 : conversation.getType());
    return type == null ? ConvType.CONV_TYPE_UNSPECIFIED : type;
  }

  private String defaultTitle(ConvType type, long peerUserId) {
    if (type == ConvType.C2C && peerUserId > 0) {
      return Long.toString(peerUserId);
    }
    if (type == ConvType.SYSTEM) {
      return "系统通知";
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
