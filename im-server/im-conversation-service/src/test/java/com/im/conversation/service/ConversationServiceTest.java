package com.im.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

  @Mock
  private ConversationMapper conversationMapper;

  @Mock
  private ConversationMemberMapper memberMapper;

  @Mock
  private ConversationCreator conversationCreator;

  private ConversationService service;

  @BeforeEach
  void setUp() {
    service = new ConversationService(
        conversationMapper,
        memberMapper,
        new C2cKeyGenerator(),
        conversationCreator);
  }

  @Test
  void returnsExistingC2cConversation() {
    ConversationEntity existing = conversation(501L, "100_200");
    when(conversationMapper.selectOne(anyWrapper())).thenReturn(existing);
    when(memberMapper.selectOne(anyWrapper())).thenReturn(member(501L, 100L));

    ConvInfo conv = resolveWithTenant(ResolveConvReq.newBuilder()
        .setFromUserId(100L)
        .setToUserId(200L)
        .build());

    assertThat(conv.getConvId()).isEqualTo(501L);
    assertThat(conv.getType()).isEqualTo(ConvType.C2C);
    assertThat(conv.getPeerUserId()).isEqualTo(200L);
    verify(conversationCreator, never()).createC2c(any(), org.mockito.Mockito.anyLong(),
        org.mockito.Mockito.anyLong());
  }

  @Test
  void createsC2cConversationWhenMissing() {
    ConversationEntity created = conversation(502L, "100_200");
    when(conversationMapper.selectOne(anyWrapper())).thenReturn(null);
    when(conversationCreator.createC2c("100_200", 100L, 200L)).thenReturn(created);
    when(memberMapper.selectOne(anyWrapper())).thenReturn(member(502L, 100L));

    ConvInfo conv = resolveWithTenant(ResolveConvReq.newBuilder()
        .setFromUserId(100L)
        .setToUserId(200L)
        .build());

    assertThat(conv.getConvId()).isEqualTo(502L);
    assertThat(conv.getPeerUserId()).isEqualTo(200L);
  }

  @Test
  void fallsBackToExistingConversationAfterDuplicateCreate() {
    ConversationEntity createdByPeer = conversation(503L, "100_200");
    when(conversationMapper.selectOne(anyWrapper())).thenReturn(null, createdByPeer);
    when(conversationCreator.createC2c("100_200", 100L, 200L))
        .thenThrow(new DuplicateKeyException("uk_tenant_c2c"));
    when(memberMapper.selectOne(anyWrapper())).thenReturn(member(503L, 100L));

    ConvInfo conv = resolveWithTenant(ResolveConvReq.newBuilder()
        .setFromUserId(100L)
        .setToUserId(200L)
        .build());

    assertThat(conv.getConvId()).isEqualTo(503L);
  }

  @Test
  void resolvesExistingConversationByIdForC2c() {
    ConversationEntity existing = conversation(504L, "100_200");
    when(conversationMapper.selectById(504L)).thenReturn(existing);
    when(memberMapper.selectOne(anyWrapper())).thenReturn(member(504L, 100L));
    when(memberMapper.selectList(anyWrapper())).thenReturn(List.of(member(504L, 100L), member(504L, 200L)));

    ConvInfo conv = resolveWithTenant(ResolveConvReq.newBuilder()
        .setFromUserId(100L)
        .setConvId(504L)
        .build());

    assertThat(conv.getConvId()).isEqualTo(504L);
    assertThat(conv.getPeerUserId()).isEqualTo(200L);
  }

  @Test
  void rejectsNonMemberWhenResolvingById() {
    ConversationEntity existing = conversation(505L, "100_200");
    when(conversationMapper.selectById(505L)).thenReturn(existing);
    when(memberMapper.selectOne(anyWrapper())).thenReturn(null);

    assertThatThrownBy(() -> resolveWithTenant(ResolveConvReq.newBuilder()
        .setFromUserId(300L)
        .setConvId(505L)
        .build()))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_CONV_MEMBER);
  }

  @Test
  void rejectsUnsupportedGroupTarget() {
    assertThatThrownBy(() -> resolveWithTenant(ResolveConvReq.newBuilder()
        .setFromUserId(100L)
        .setGroupId(10L)
        .build()))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void listsMemberConversationsForFullSync() {
    ConversationEntity existing = conversation(501L, "100_200");
    existing.setMaxSeq(3L);
    when(memberMapper.selectList(anyWrapper()))
        .thenReturn(List.of(member(501L, 100L)), List.of(member(501L, 100L), member(501L, 200L)));
    when(conversationMapper.selectById(501L)).thenReturn(existing);

    AtomicReference<List<ConvInfo>> result = new AtomicReference<>();
    TenantContext.runWithTenant(1L, () -> result.set(service.listMemberConvs(100L, 100)));

    assertThat(result.get()).hasSize(1);
    assertThat(result.get().getFirst().getConvId()).isEqualTo(501L);
    assertThat(result.get().getFirst().getType()).isEqualTo(ConvType.C2C);
    assertThat(result.get().getFirst().getTitle()).isEqualTo("200");
    assertThat(result.get().getFirst().getPeerUserId()).isEqualTo(200L);
    assertThat(result.get().getFirst().getMaxSeq()).isEqualTo(3L);
  }

  private ConvInfo resolveWithTenant(ResolveConvReq request) {
    AtomicReference<ConvInfo> result = new AtomicReference<>();
    TenantContext.runWithTenant(1L, () -> result.set(service.resolve(request)));
    return result.get();
  }

  @SuppressWarnings("unchecked")
  private <T> Wrapper<T> anyWrapper() {
    return any(Wrapper.class);
  }

  private ConversationEntity conversation(long id, String c2cKey) {
    ConversationEntity conversation = new ConversationEntity();
    conversation.setId(id);
    conversation.setType(ConvType.C2C.getNumber());
    conversation.setC2cKey(c2cKey);
    conversation.setMaxSeq(0L);
    conversation.setLastMsgAbstract("");
    return conversation;
  }

  private ConversationMemberEntity member(long conversationId, long userId) {
    ConversationMemberEntity member = new ConversationMemberEntity();
    member.setConvId(conversationId);
    member.setUserId(userId);
    member.setReadSeq(0L);
    member.setPinned(0);
    member.setMuted(0);
    return member;
  }
}
