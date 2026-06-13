package com.im.group.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.id.SnowflakeIdGenerator;
import com.im.common.outbox.OutboxWriter;
import com.im.common.tenant.TenantContext;
import com.im.group.dao.entity.GroupConversationEntity;
import com.im.group.dao.entity.GroupConversationMemberEntity;
import com.im.group.dao.entity.GroupInfoEntity;
import com.im.group.dao.entity.GroupMemberEntity;
import com.im.group.dao.entity.GroupMessageEntity;
import com.im.group.dao.entity.TenantConfigEntity;
import com.im.group.dao.mapper.GroupConversationMapper;
import com.im.group.dao.mapper.GroupConversationMemberMapper;
import com.im.group.dao.mapper.GroupInfoMapper;
import com.im.group.dao.mapper.GroupMemberMapper;
import com.im.group.dao.mapper.GroupMessageMapper;
import com.im.group.dao.mapper.TenantConfigMapper;
import com.im.group.dto.AddGroupMembersRequest;
import com.im.group.dto.CreateGroupRequest;
import com.im.group.dto.GroupMemberChangeResponse;
import com.im.group.dto.GroupResponse;
import com.im.group.dto.UpdateGroupRequest;
import com.im.proto.body.MsgPush;
import com.im.proto.body.Sender;
import com.im.proto.common.ConvType;
import com.im.proto.common.MsgContent;
import com.im.proto.common.MsgStatus;
import com.im.proto.common.NotificationContent;
import com.im.proto.events.MsgSavedEvent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupService {

  private static final int DEFAULT_MAX_GROUP_MEMBERS = 500;
  private static final int GROUP_STATUS_NORMAL = 1;
  private static final int ROLE_MEMBER = 1;
  private static final int ROLE_ADMIN = 2;
  private static final int ROLE_OWNER = 3;
  private static final int MSG_TYPE_NOTIFICATION = 10;
  private static final String EVENT_MSG_SAVED = "msg.saved";

  private final GroupInfoMapper groupInfoMapper;
  private final GroupMemberMapper groupMemberMapper;
  private final TenantConfigMapper tenantConfigMapper;
  private final GroupConversationMapper conversationMapper;
  private final GroupConversationMemberMapper conversationMemberMapper;
  private final GroupMessageMapper messageMapper;
  private final SnowflakeIdGenerator idGenerator;
  private final OutboxWriter outboxWriter;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Autowired
  public GroupService(GroupInfoMapper groupInfoMapper,
      GroupMemberMapper groupMemberMapper,
      TenantConfigMapper tenantConfigMapper,
      GroupConversationMapper conversationMapper,
      GroupConversationMemberMapper conversationMemberMapper,
      GroupMessageMapper messageMapper,
      SnowflakeIdGenerator idGenerator,
      OutboxWriter outboxWriter,
      ObjectMapper objectMapper) {
    this(groupInfoMapper, groupMemberMapper, tenantConfigMapper, conversationMapper,
        conversationMemberMapper, messageMapper, idGenerator, outboxWriter, objectMapper,
        Clock.systemUTC());
  }

  GroupService(GroupInfoMapper groupInfoMapper,
      GroupMemberMapper groupMemberMapper,
      TenantConfigMapper tenantConfigMapper,
      GroupConversationMapper conversationMapper,
      GroupConversationMemberMapper conversationMemberMapper,
      GroupMessageMapper messageMapper,
      SnowflakeIdGenerator idGenerator,
      OutboxWriter outboxWriter,
      ObjectMapper objectMapper,
      Clock clock) {
    this.groupInfoMapper = groupInfoMapper;
    this.groupMemberMapper = groupMemberMapper;
    this.tenantConfigMapper = tenantConfigMapper;
    this.conversationMapper = conversationMapper;
    this.conversationMemberMapper = conversationMemberMapper;
    this.messageMapper = messageMapper;
    this.idGenerator = idGenerator;
    this.outboxWriter = outboxWriter;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional
  public GroupResponse createGroup(long operatorUserId, CreateGroupRequest request) {
    long tenantId = TenantContext.requiredTenantId();
    validateUserId(operatorUserId, "operator_user_id");
    String name = normalizeName(request.name());
    List<Long> memberIds = initialMembers(operatorUserId, request.memberUserIds());
    ensureWithinLimit(memberIds.size(), maxGroupMembers());

    long groupId = idGenerator.nextId();
    long conversationId = idGenerator.nextId();
    GroupInfoEntity group = newGroup(tenantId, groupId, name, operatorUserId, memberIds.size());
    groupInfoMapper.insert(group);

    for (long userId : memberIds) {
      groupMemberMapper.insert(groupMember(tenantId, groupId, userId,
          userId == operatorUserId ? ROLE_OWNER : ROLE_MEMBER));
    }

    GroupConversationEntity conversation = newConversation(tenantId, conversationId, groupId);
    conversationMapper.insert(conversation);
    for (long userId : memberIds) {
      conversationMemberMapper.insert(conversationMember(tenantId, conversationId, userId, 0L));
    }

    appendNotification(tenantId, conversationId, groupId, operatorUserId, "group.created",
        payload("group_id", groupId, "name", name, "operator", operatorUserId,
            "user_ids", memberIds),
        "created group " + name);
    return toResponse(group, conversationId);
  }

  @Transactional
  public GroupMemberChangeResponse addMembers(long operatorUserId, long groupId,
      AddGroupMembersRequest request) {
    long tenantId = TenantContext.requiredTenantId();
    validateUserId(operatorUserId, "operator_user_id");
    GroupContext context = loadAuthorizedGroup(operatorUserId, groupId);
    List<Long> requested = normalizeUserIds(request.userIds());
    Set<Long> existing = existingMemberIds(groupId, requested);
    List<Long> toAdd = requested.stream()
        .filter(userId -> !existing.contains(userId))
        .toList();
    if (toAdd.isEmpty()) {
      return new GroupMemberChangeResponse(groupId, context.conversationId(),
          context.group().getMemberCount(), List.of());
    }

    int nextCount = context.group().getMemberCount() + toAdd.size();
    ensureWithinLimit(nextCount, maxGroupMembers());
    long readSeq = currentMaxSeq(context.conversationId());
    for (long userId : toAdd) {
      groupMemberMapper.insert(groupMember(tenantId, groupId, userId, ROLE_MEMBER));
      upsertConversationMember(tenantId, context.conversationId(), userId, readSeq);
    }
    context.group().setMemberCount(nextCount);
    groupInfoMapper.updateById(context.group());
    appendNotification(tenantId, context.conversationId(), groupId, operatorUserId,
        "group.member_added",
        payload("group_id", groupId, "operator", operatorUserId, "user_ids", toAdd),
        "added " + toAdd.size() + " group members");
    return new GroupMemberChangeResponse(groupId, context.conversationId(), nextCount, toAdd);
  }

  @Transactional
  public GroupMemberChangeResponse removeMember(long operatorUserId, long groupId, long userId) {
    long tenantId = TenantContext.requiredTenantId();
    validateUserId(operatorUserId, "operator_user_id");
    validateUserId(userId, "user_id");
    GroupContext context = loadAuthorizedGroup(operatorUserId, groupId);
    if (userId == context.group().getOwnerId()) {
      throw new ImException(ErrorCode.NO_PERMISSION, "group owner cannot be removed");
    }
    GroupMemberEntity target = findGroupMember(groupId, userId);
    if (target == null) {
      throw new ImException(ErrorCode.NOT_GROUP_MEMBER);
    }

    groupMemberMapper.delete(Wrappers.lambdaQuery(GroupMemberEntity.class)
        .eq(GroupMemberEntity::getGroupId, groupId)
        .eq(GroupMemberEntity::getUserId, userId));
    GroupConversationMemberEntity patch = new GroupConversationMemberEntity();
    patch.setDeletedAt(now());
    conversationMemberMapper.update(patch, Wrappers.lambdaUpdate(GroupConversationMemberEntity.class)
        .eq(GroupConversationMemberEntity::getConvId, context.conversationId())
        .eq(GroupConversationMemberEntity::getUserId, userId));

    int nextCount = Math.max(context.group().getMemberCount() - 1, 0);
    context.group().setMemberCount(nextCount);
    groupInfoMapper.updateById(context.group());
    appendNotification(tenantId, context.conversationId(), groupId, operatorUserId,
        "group.member_removed",
        payload("group_id", groupId, "operator", operatorUserId, "user_ids", List.of(userId)),
        "removed 1 group member");
    return new GroupMemberChangeResponse(groupId, context.conversationId(), nextCount, List.of(userId));
  }

  @Transactional
  public GroupResponse rename(long operatorUserId, long groupId, UpdateGroupRequest request) {
    long tenantId = TenantContext.requiredTenantId();
    validateUserId(operatorUserId, "operator_user_id");
    GroupContext context = loadAuthorizedGroup(operatorUserId, groupId);
    String oldName = context.group().getName();
    String newName = normalizeName(request.name());
    if (oldName.equals(newName)) {
      return toResponse(context.group(), context.conversationId());
    }
    context.group().setName(newName);
    groupInfoMapper.updateById(context.group());
    appendNotification(tenantId, context.conversationId(), groupId, operatorUserId,
        "group.name_changed",
        payload("group_id", groupId, "operator", operatorUserId, "old", oldName, "new", newName),
        "renamed group to " + newName);
    return toResponse(context.group(), context.conversationId());
  }

  private GroupInfoEntity newGroup(long tenantId, long groupId, String name, long ownerId,
      int memberCount) {
    GroupInfoEntity group = new GroupInfoEntity();
    group.setId(groupId);
    group.setTenantId(tenantId);
    group.setName(name);
    group.setOwnerId(ownerId);
    group.setAvatar("");
    group.setMemberCount(memberCount);
    group.setStatus(GROUP_STATUS_NORMAL);
    return group;
  }

  private GroupConversationEntity newConversation(long tenantId, long conversationId, long groupId) {
    GroupConversationEntity conversation = new GroupConversationEntity();
    conversation.setId(conversationId);
    conversation.setTenantId(tenantId);
    conversation.setType(ConvType.GROUP.getNumber());
    conversation.setGroupId(groupId);
    conversation.setMaxSeq(0L);
    conversation.setLastMsgAbstract("");
    return conversation;
  }

  private GroupMemberEntity groupMember(long tenantId, long groupId, long userId, int role) {
    GroupMemberEntity member = new GroupMemberEntity();
    member.setGroupId(groupId);
    member.setTenantId(tenantId);
    member.setUserId(userId);
    member.setRole(role);
    return member;
  }

  private GroupConversationMemberEntity conversationMember(long tenantId, long conversationId,
      long userId, long readSeq) {
    GroupConversationMemberEntity member = new GroupConversationMemberEntity();
    member.setConvId(conversationId);
    member.setTenantId(tenantId);
    member.setUserId(userId);
    member.setReadSeq(readSeq);
    member.setPinned(0);
    member.setMuted(0);
    return member;
  }

  private void upsertConversationMember(long tenantId, long conversationId, long userId,
      long readSeq) {
    GroupConversationMemberEntity existing = conversationMemberMapper.selectOne(Wrappers
        .lambdaQuery(GroupConversationMemberEntity.class)
        .eq(GroupConversationMemberEntity::getConvId, conversationId)
        .eq(GroupConversationMemberEntity::getUserId, userId));
    if (existing == null) {
      conversationMemberMapper.insert(conversationMember(tenantId, conversationId, userId, readSeq));
      return;
    }
    GroupConversationMemberEntity patch = new GroupConversationMemberEntity();
    patch.setReadSeq(readSeq);
    patch.setDeletedAt(null);
    conversationMemberMapper.update(patch, Wrappers.lambdaUpdate(GroupConversationMemberEntity.class)
        .set(GroupConversationMemberEntity::getReadSeq, readSeq)
        .set(GroupConversationMemberEntity::getDeletedAt, null)
        .eq(GroupConversationMemberEntity::getConvId, conversationId)
        .eq(GroupConversationMemberEntity::getUserId, userId));
  }

  private void appendNotification(long tenantId, long conversationId, long groupId,
      long operatorUserId, String eventType, String payload, String abstractText) {
    int incremented = conversationMapper.incrementMaxSeq(conversationId);
    if (incremented != 1) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "conversation seq update failed");
    }
    long seq = currentMaxSeq(conversationId);
    LocalDateTime createdAt = now();
    MsgContent content = MsgContent.newBuilder()
        .setNotification(NotificationContent.newBuilder()
            .setEventType(eventType)
            .setPayload(payload))
        .build();

    GroupMessageEntity message = new GroupMessageEntity();
    message.setId(idGenerator.nextId());
    message.setTenantId(tenantId);
    message.setConversationId(conversationId);
    message.setSeq(seq);
    message.setSenderId(operatorUserId);
    message.setClientMsgId("sys-" + idGenerator.nextId());
    message.setMsgType(MSG_TYPE_NOTIFICATION);
    message.setContent(content.toByteArray());
    message.setAbstractText(limitAbstract(abstractText));
    message.setStatus(MsgStatus.NORMAL.getNumber());
    message.setCreatedAt(createdAt);
    messageMapper.insert(message);

    int updated = conversationMapper.updateLastMessage(conversationId, seq,
        message.getAbstractText(), createdAt);
    if (updated != 1) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "conversation progress update failed");
    }

    MsgPush push = MsgPush.newBuilder()
        .setConvId(conversationId)
        .setConvType(ConvType.GROUP)
        .setSeq(seq)
        .setServerMsgId(message.getId())
        .setClientMsgId(message.getClientMsgId())
        .setSender(Sender.newBuilder().setUserId(operatorUserId))
        .setSendTime(createdAt.toInstant(ZoneOffset.UTC).toEpochMilli())
        .setContent(content)
        .putExt("group_id", Long.toString(groupId))
        .build();
    MsgSavedEvent event = MsgSavedEvent.newBuilder()
        .setTenantId(tenantId)
        .setConvId(conversationId)
        .setSeq(seq)
        .setServerMsgId(message.getId())
        .setSenderId(operatorUserId)
        .setPushReady(push)
        .build();
    outboxWriter.write(tenantId, EVENT_MSG_SAVED, EVENT_MSG_SAVED + "." + tenantId,
        event.toByteArray());
  }

  private GroupContext loadAuthorizedGroup(long operatorUserId, long groupId) {
    GroupInfoEntity group = loadGroup(groupId);
    GroupMemberEntity operatorMember = findGroupMember(groupId, operatorUserId);
    if (operatorMember == null) {
      throw new ImException(ErrorCode.NOT_GROUP_MEMBER);
    }
    if (!canManage(operatorMember)) {
      throw new ImException(ErrorCode.NO_PERMISSION);
    }
    GroupConversationEntity conversation = findConversationByGroupId(groupId);
    if (conversation == null) {
      throw new ImException(ErrorCode.CONV_NOT_FOUND);
    }
    return new GroupContext(group, conversation.getId());
  }

  private GroupInfoEntity loadGroup(long groupId) {
    if (groupId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "group_id must be positive");
    }
    GroupInfoEntity group = groupInfoMapper.selectById(groupId);
    if (group == null || !Integer.valueOf(GROUP_STATUS_NORMAL).equals(group.getStatus())) {
      throw new ImException(ErrorCode.GROUP_NOT_FOUND);
    }
    return group;
  }

  private GroupConversationEntity findConversationByGroupId(long groupId) {
    return conversationMapper.selectOne(Wrappers.lambdaQuery(GroupConversationEntity.class)
        .eq(GroupConversationEntity::getGroupId, groupId)
        .eq(GroupConversationEntity::getType, ConvType.GROUP.getNumber()));
  }

  private GroupMemberEntity findGroupMember(long groupId, long userId) {
    return groupMemberMapper.selectOne(Wrappers.lambdaQuery(GroupMemberEntity.class)
        .eq(GroupMemberEntity::getGroupId, groupId)
        .eq(GroupMemberEntity::getUserId, userId));
  }

  private Set<Long> existingMemberIds(long groupId, List<Long> userIds) {
    if (userIds.isEmpty()) {
      return Set.of();
    }
    List<GroupMemberEntity> existing = groupMemberMapper.selectList(Wrappers
        .lambdaQuery(GroupMemberEntity.class)
        .eq(GroupMemberEntity::getGroupId, groupId)
        .in(GroupMemberEntity::getUserId, userIds));
    Set<Long> result = new LinkedHashSet<>();
    existing.forEach(member -> result.add(member.getUserId()));
    return result;
  }

  private boolean canManage(GroupMemberEntity operatorMember) {
    Integer role = operatorMember.getRole();
    return role != null && (role == ROLE_OWNER || role == ROLE_ADMIN);
  }

  private int maxGroupMembers() {
    long tenantId = TenantContext.requiredTenantId();
    TenantConfigEntity config = tenantConfigMapper.selectById(tenantId);
    if (config == null || config.getMaxGroupMembers() == null || config.getMaxGroupMembers() <= 0) {
      return DEFAULT_MAX_GROUP_MEMBERS;
    }
    return config.getMaxGroupMembers();
  }

  private void ensureWithinLimit(int memberCount, int maxGroupMembers) {
    if (memberCount > maxGroupMembers) {
      throw new ImException(ErrorCode.GROUP_FULL);
    }
  }

  private long currentMaxSeq(long conversationId) {
    Long maxSeq = conversationMapper.selectMaxSeq(conversationId);
    if (maxSeq == null) {
      throw new ImException(ErrorCode.CONV_NOT_FOUND);
    }
    return maxSeq;
  }

  private List<Long> initialMembers(long ownerId, List<Long> rawUserIds) {
    List<Long> memberIds = new ArrayList<>();
    memberIds.add(ownerId);
    if (rawUserIds != null) {
      memberIds.addAll(rawUserIds);
    }
    return normalizeUserIds(memberIds);
  }

  private List<Long> normalizeUserIds(List<Long> rawUserIds) {
    LinkedHashSet<Long> ids = new LinkedHashSet<>();
    if (rawUserIds != null) {
      for (Long rawUserId : rawUserIds) {
        if (rawUserId == null || rawUserId <= 0) {
          throw new ImException(ErrorCode.VALIDATION_FAILED, "user_id must be positive");
        }
        ids.add(rawUserId);
      }
    }
    if (ids.isEmpty()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "user_ids must not be empty");
    }
    return List.copyOf(ids);
  }

  private String normalizeName(String name) {
    String normalized = name == null ? "" : name.trim();
    if (normalized.isBlank()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "group name is required");
    }
    if (normalized.length() > 128) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "group name is too long");
    }
    return normalized;
  }

  private void validateUserId(long userId, String field) {
    if (userId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, field + " must be positive");
    }
  }

  private String payload(Object... keyValues) {
    if (keyValues.length % 2 != 0) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "invalid notification payload");
    }
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put((String) keyValues[i], keyValues[i + 1]);
    }
    try {
      return objectMapper.writeValueAsString(map);
    } catch (JsonProcessingException ex) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "failed to encode notification payload", ex);
    }
  }

  private GroupResponse toResponse(GroupInfoEntity group, long conversationId) {
    return new GroupResponse(
        group.getId(),
        conversationId,
        group.getName(),
        group.getOwnerId(),
        group.getMemberCount());
  }

  private String limitAbstract(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String normalized = value.replaceAll("\\s+", " ").trim();
    return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private record GroupContext(GroupInfoEntity group, long conversationId) {
  }
}
