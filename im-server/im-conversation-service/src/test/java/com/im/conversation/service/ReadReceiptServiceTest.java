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
import com.im.proto.body.ReadNotify;
import com.im.proto.body.ReadReport;
import com.im.proto.common.ConvType;
import com.im.proto.rpc.ConnCtx;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadReceiptServiceTest {

  @Mock
  private ConversationMapper conversationMapper;

  @Mock
  private ConversationMemberMapper memberMapper;

  @Mock
  private ReadReceiptPusher readReceiptPusher;

  private ReadReceiptService service;

  @BeforeEach
  void setUp() {
    service = new ReadReceiptService(conversationMapper, memberMapper, readReceiptPusher);
  }

  @Test
  void advancesReadSeqAndPushesNotifyToConversationMembers() {
    when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, 3L));
    when(memberMapper.selectOne(anyWrapper()))
        .thenReturn(member(501L, 100L, 0L), member(501L, 100L, 2L));
    when(memberMapper.update(any(), anyWrapper())).thenReturn(1);
    when(memberMapper.selectList(anyWrapper()))
        .thenReturn(List.of(member(501L, 100L, 2L), member(501L, 200L, 0L)));

    ReadReceiptResult result = report(501L, 2L);

    assertThat(result.changed()).isTrue();
    assertThat(result.readNotify().getConvId()).isEqualTo(501L);
    assertThat(result.readNotify().getReaderUserId()).isEqualTo(100L);
    assertThat(result.readNotify().getReadSeq()).isEqualTo(2L);
    ArgumentCaptor<ConversationMemberEntity> updateCaptor =
        ArgumentCaptor.forClass(ConversationMemberEntity.class);
    verify(memberMapper).update(updateCaptor.capture(), anyWrapper());
    assertThat(updateCaptor.getValue().getReadSeq()).isEqualTo(2L);
    verify(readReceiptPusher).pushReadNotify(ctx(), List.of(100L, 200L), result.readNotify());
  }

  @Test
  void ignoresReadSeqRollbackWithoutPush() {
    when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, 3L));
    when(memberMapper.selectOne(anyWrapper())).thenReturn(member(501L, 100L, 2L));

    ReadReceiptResult result = report(501L, 1L);

    assertThat(result.changed()).isFalse();
    assertThat(result.readNotify().getReadSeq()).isEqualTo(2L);
    verify(memberMapper, never()).update(any(), anyWrapper());
    verify(readReceiptPusher, never()).pushReadNotify(any(), any(), any());
  }

  @Test
  void rejectsReadSeqBeyondConversationMaxSeq() {
    when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, 3L));

    assertThatThrownBy(() -> report(501L, 4L))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
    verify(readReceiptPusher, never()).pushReadNotify(any(), any(), any());
  }

  @Test
  void rejectsNonMember() {
    when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, 3L));
    when(memberMapper.selectOne(anyWrapper())).thenReturn(null);

    assertThatThrownBy(() -> report(501L, 2L))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_CONV_MEMBER);
  }

  private ReadReceiptResult report(long conversationId, long readSeq) {
    AtomicReference<ReadReceiptResult> result = new AtomicReference<>();
    TenantContext.runWithTenant(1L, () -> result.set(service.reportRead(ctx(), ReadReport.newBuilder()
        .setConvId(conversationId)
        .setReadSeq(readSeq)
        .build())));
    return result.get();
  }

  @SuppressWarnings("unchecked")
  private <T> Wrapper<T> anyWrapper() {
    return any(Wrapper.class);
  }

  private ConversationEntity conversation(long id, long maxSeq) {
    ConversationEntity conversation = new ConversationEntity();
    conversation.setId(id);
    conversation.setType(ConvType.C2C.getNumber());
    conversation.setMaxSeq(maxSeq);
    return conversation;
  }

  private ConversationMemberEntity member(long conversationId, long userId, long readSeq) {
    ConversationMemberEntity member = new ConversationMemberEntity();
    member.setConvId(conversationId);
    member.setUserId(userId);
    member.setReadSeq(readSeq);
    member.setPinned(0);
    member.setMuted(0);
    return member;
  }

  private ConnCtx ctx() {
    return ConnCtx.newBuilder()
        .setTenantId(1L)
        .setUserId(100L)
        .setPlatform(1)
        .setDeviceId("device-a")
        .setConnId("conn-a")
        .setGwInstance("gw-a")
        .build();
  }
}
