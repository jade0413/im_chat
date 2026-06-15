package com.im.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.conversation.UserConvEventType;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.id.SnowflakeIdGenerator;
import com.im.common.outbox.OutboxWriter;
import com.im.common.sequence.ConversationSequenceService;
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
import com.im.proto.common.ConvType;
import com.im.proto.common.MsgContent;
import com.im.proto.events.MsgSavedEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

  @Mock
  private GroupInfoMapper groupInfoMapper;

  @Mock
  private GroupMemberMapper groupMemberMapper;

  @Mock
  private TenantConfigMapper tenantConfigMapper;

  @Mock
  private GroupConversationMapper conversationMapper;

  @Mock
  private GroupConversationMemberMapper conversationMemberMapper;

  @Mock
  private GroupMessageMapper messageMapper;

  @Mock
  private ConversationSequenceService sequenceService;

  @Mock
  private SnowflakeIdGenerator idGenerator;

  @Mock
  private OutboxWriter outboxWriter;

  @Mock
  private GroupUserConvEventRecorder userConvEventRecorder;

  @Mock
  private com.im.common.conversation.ConversationMemberCache memberCache;

  @Captor
  private ArgumentCaptor<GroupInfoEntity> groupCaptor;

  @Captor
  private ArgumentCaptor<GroupConversationEntity> conversationCaptor;

  @Captor
  private ArgumentCaptor<GroupMemberEntity> groupMemberCaptor;

  @Captor
  private ArgumentCaptor<GroupConversationMemberEntity> conversationMemberCaptor;

  @Captor
  private ArgumentCaptor<GroupMessageEntity> messageCaptor;

  @Captor
  private ArgumentCaptor<byte[]> payloadCaptor;

  private GroupService service;

  @BeforeEach
  void setUp() {
    service = new GroupService(
        groupInfoMapper,
        groupMemberMapper,
        tenantConfigMapper,
        conversationMapper,
        conversationMemberMapper,
        messageMapper,
        sequenceService,
        idGenerator,
        outboxWriter,
        userConvEventRecorder,
        new ObjectMapper(),
        memberCache,
        Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void createsGroupConversationMembersAndNotificationInOneCall() throws Exception {
    when(idGenerator.nextId()).thenReturn(10L, 20L, 30L, 40L);
    when(tenantConfigMapper.selectById(1L)).thenReturn(config(500));
    when(sequenceService.nextSeq(20L)).thenReturn(1L);
    when(conversationMapper.updateLastMessage(eq(20L), eq(1L), any(), any())).thenReturn(1);

    GroupResponse response = withTenant(() -> service.createGroup(
        100L,
        new CreateGroupRequest("team", List.of(200L, 300L))));

    assertThat(response.groupId()).isEqualTo(10L);
    assertThat(response.convId()).isEqualTo(20L);
    assertThat(response.memberCount()).isEqualTo(3);

    verify(groupInfoMapper).insert(groupCaptor.capture());
    assertThat(groupCaptor.getValue().getName()).isEqualTo("team");
    assertThat(groupCaptor.getValue().getOwnerId()).isEqualTo(100L);
    assertThat(groupCaptor.getValue().getMemberCount()).isEqualTo(3);

    verify(conversationMapper).insert(conversationCaptor.capture());
    assertThat(conversationCaptor.getValue().getType()).isEqualTo(ConvType.GROUP.getNumber());
    assertThat(conversationCaptor.getValue().getGroupId()).isEqualTo(10L);

    verify(groupMemberMapper, org.mockito.Mockito.times(3)).insert(groupMemberCaptor.capture());
    assertThat(groupMemberCaptor.getAllValues()).extracting(GroupMemberEntity::getUserId)
        .containsExactly(100L, 200L, 300L);
    assertThat(groupMemberCaptor.getAllValues().getFirst().getRole()).isEqualTo(3);

    verify(conversationMemberMapper, org.mockito.Mockito.times(3)).insert(
        conversationMemberCaptor.capture());
    assertThat(conversationMemberCaptor.getAllValues()).extracting(GroupConversationMemberEntity::getConvId)
        .containsExactly(20L, 20L, 20L);
    verify(userConvEventRecorder).record(1L, 100L, 20L, UserConvEventType.CREATED);
    verify(userConvEventRecorder).record(1L, 200L, 20L, UserConvEventType.CREATED);
    verify(userConvEventRecorder).record(1L, 300L, 20L, UserConvEventType.CREATED);

    verify(messageMapper).insert(messageCaptor.capture());
    MsgContent content = MsgContent.parseFrom(messageCaptor.getValue().getContent());
    assertThat(content.getNotification().getEventType()).isEqualTo("group.created");

    verify(outboxWriter).write(eq(1L), eq("msg.saved"), eq("msg.saved.1"), payloadCaptor.capture());
    MsgSavedEvent event = MsgSavedEvent.parseFrom(payloadCaptor.getValue());
    assertThat(event.getTenantId()).isEqualTo(1L);
    assertThat(event.getConvId()).isEqualTo(20L);
    assertThat(event.getSeq()).isEqualTo(1L);
    assertThat(event.getPushReady().getConvType()).isEqualTo(ConvType.GROUP);
  }

  @Test
  void rejectsCreateWhenGroupWouldExceedTenantLimit() {
    when(tenantConfigMapper.selectById(1L)).thenReturn(config(2));

    assertThatThrownBy(() -> withTenant(() -> service.createGroup(
        100L,
        new CreateGroupRequest("team", List.of(200L, 300L)))))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.GROUP_FULL);
    verify(groupInfoMapper, never()).insert(any(GroupInfoEntity.class));
  }

  @Test
  void addMembersSkipsExistingMembersAndWritesNotification() throws Exception {
    GroupInfoEntity group = group(10L, "team", 100L, 2);
    when(groupInfoMapper.selectById(10L)).thenReturn(group);
    when(groupMemberMapper.selectOne(anyWrapper())).thenReturn(member(10L, 100L, 3));
    when(conversationMapper.selectOne(anyWrapper())).thenReturn(conversation(20L, 10L));
    when(groupMemberMapper.selectList(anyWrapper())).thenReturn(List.of(member(10L, 200L, 1)));
    when(tenantConfigMapper.selectById(1L)).thenReturn(config(500));
    when(sequenceService.currentSeq(20L)).thenReturn(5L);
    when(sequenceService.nextSeq(20L)).thenReturn(6L);
    when(conversationMapper.updateLastMessage(eq(20L), eq(6L), any(), any())).thenReturn(1);
    when(conversationMemberMapper.selectOne(anyWrapper())).thenReturn(null);
    when(idGenerator.nextId()).thenReturn(30L, 40L);

    GroupMemberChangeResponse response = withTenant(() -> service.addMembers(
        100L,
        10L,
        new AddGroupMembersRequest(List.of(200L, 300L))));

    assertThat(response.changedUserIds()).containsExactly(300L);
    assertThat(response.memberCount()).isEqualTo(3);
    verify(groupMemberMapper).insert(groupMemberCaptor.capture());
    assertThat(groupMemberCaptor.getValue().getUserId()).isEqualTo(300L);
    verify(conversationMemberMapper).insert(conversationMemberCaptor.capture());
    assertThat(conversationMemberCaptor.getValue().getReadSeq()).isEqualTo(5L);
    verify(userConvEventRecorder).record(1L, 300L, 20L, UserConvEventType.CREATED);
    verify(messageMapper).insert(messageCaptor.capture());
    MsgContent content = MsgContent.parseFrom(messageCaptor.getValue().getContent());
    assertThat(content.getNotification().getEventType()).isEqualTo("group.member_added");
  }

  @Test
  void rejectsRemovingOwner() {
    when(groupInfoMapper.selectById(10L)).thenReturn(group(10L, "team", 100L, 2));
    when(groupMemberMapper.selectOne(anyWrapper())).thenReturn(member(10L, 100L, 3));
    when(conversationMapper.selectOne(anyWrapper())).thenReturn(conversation(20L, 10L));

    assertThatThrownBy(() -> withTenant(() -> service.removeMember(100L, 10L, 100L)))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NO_PERMISSION);
    verify(groupMemberMapper, never()).delete(anyWrapper());
  }

  @Test
  void removeMemberSoftDeletesConversationMemberAndWritesNotification() throws Exception {
    GroupInfoEntity group = group(10L, "team", 100L, 3);
    when(groupInfoMapper.selectById(10L)).thenReturn(group);
    when(groupMemberMapper.selectOne(anyWrapper()))
        .thenReturn(member(10L, 100L, 3), member(10L, 200L, 1));
    when(conversationMapper.selectOne(anyWrapper())).thenReturn(conversation(20L, 10L));
    when(sequenceService.nextSeq(20L)).thenReturn(7L);
    when(conversationMapper.updateLastMessage(eq(20L), eq(7L), any(), any())).thenReturn(1);
    when(idGenerator.nextId()).thenReturn(30L, 40L);

    GroupMemberChangeResponse response = withTenant(() -> service.removeMember(100L, 10L, 200L));

    assertThat(response.changedUserIds()).containsExactly(200L);
    assertThat(response.memberCount()).isEqualTo(2);
    verify(groupMemberMapper).delete(anyWrapper());
    verify(conversationMemberMapper).update(conversationMemberCaptor.capture(), anyWrapper());
    assertThat(conversationMemberCaptor.getValue().getDeletedAt()).isNotNull();
    verify(userConvEventRecorder).record(1L, 200L, 20L, UserConvEventType.REMOVED);
    verify(groupInfoMapper).updateById(groupCaptor.capture());
    assertThat(groupCaptor.getValue().getMemberCount()).isEqualTo(2);
    verify(messageMapper).insert(messageCaptor.capture());
    MsgContent content = MsgContent.parseFrom(messageCaptor.getValue().getContent());
    assertThat(content.getNotification().getEventType()).isEqualTo("group.member_removed");
    verify(outboxWriter).write(eq(1L), eq("msg.saved"), eq("msg.saved.1"), payloadCaptor.capture());
    MsgSavedEvent event = MsgSavedEvent.parseFrom(payloadCaptor.getValue());
    assertThat(event.getPushReady().getConvType()).isEqualTo(ConvType.GROUP);
    assertThat(event.getPushReady().getSeq()).isEqualTo(7L);
  }

  private <T> T withTenant(java.util.concurrent.Callable<T> callable) {
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<RuntimeException> error = new AtomicReference<>();
    TenantContext.runWithTenant(1L, () -> {
      try {
        result.set(callable.call());
      } catch (RuntimeException ex) {
        error.set(ex);
      } catch (Exception ex) {
        error.set(new RuntimeException(ex));
      }
    });
    if (error.get() != null) {
      throw error.get();
    }
    return result.get();
  }

  @SuppressWarnings("unchecked")
  private <T> Wrapper<T> anyWrapper() {
    return any(Wrapper.class);
  }

  private TenantConfigEntity config(int maxGroupMembers) {
    TenantConfigEntity config = new TenantConfigEntity();
    config.setTenantId(1L);
    config.setMaxGroupMembers(maxGroupMembers);
    return config;
  }

  private GroupInfoEntity group(long id, String name, long ownerId, int memberCount) {
    GroupInfoEntity group = new GroupInfoEntity();
    group.setId(id);
    group.setName(name);
    group.setOwnerId(ownerId);
    group.setMemberCount(memberCount);
    group.setStatus(1);
    return group;
  }

  private GroupMemberEntity member(long groupId, long userId, int role) {
    GroupMemberEntity member = new GroupMemberEntity();
    member.setGroupId(groupId);
    member.setUserId(userId);
    member.setRole(role);
    return member;
  }

  private GroupConversationEntity conversation(long conversationId, long groupId) {
    GroupConversationEntity conversation = new GroupConversationEntity();
    conversation.setId(conversationId);
    conversation.setType(ConvType.GROUP.getNumber());
    conversation.setGroupId(groupId);
    conversation.setMaxSeq(0L);
    return conversation;
  }
}
