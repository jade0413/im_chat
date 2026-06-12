package com.im.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.message.dao.entity.MessageEntity;
import com.im.message.dao.mapper.MessageMapper;
import com.im.proto.body.MsgPush;
import com.im.proto.body.SyncReq;
import com.im.proto.body.SyncResp;
import com.im.proto.common.MsgContent;
import com.im.proto.common.TextContent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageQueryServiceTest {

  @Mock
  private MessageMapper messageMapper;

  @Mock
  private MessageAssembler assembler;

  @Mock
  private ConversationMemberClient memberClient;

  private MessageQueryService service;

  @BeforeEach
  void setUp() {
    service = new MessageQueryService(messageMapper, assembler, memberClient);
  }

  @Test
  void syncReturnsGapMessagesAndHasMore() {
    MessageEntity latest = message(3L);
    MessageEntity first = message(2L);
    MessageEntity second = message(3L);
    when(memberClient.getMemberUserIds(501L)).thenReturn(List.of(100L, 200L));
    when(messageMapper.selectList(anyWrapper())).thenReturn(List.of(latest), List.of(first, second));
    when(assembler.toPush(first)).thenReturn(push(2L));
    when(assembler.toPush(second)).thenReturn(push(3L));

    SyncResp response = syncWithTenant(100L, SyncReq.newBuilder()
        .addConvVersions(SyncReq.ConvVersion.newBuilder()
            .setConvId(501L)
            .setLocalMaxSeq(1L))
        .build());

    assertThat(response.getDeltasList()).hasSize(1);
    assertThat(response.getDeltas(0).getServerMaxSeq()).isEqualTo(3L);
    assertThat(response.getDeltas(0).getMsgsList()).extracting(MsgPush::getSeq)
        .containsExactly(2L, 3L);
    assertThat(response.getDeltas(0).getHasMore()).isFalse();
  }

  @Test
  void historyReturnsDescendingPageAndHasMore() {
    MessageEntity newest = message(5L);
    MessageEntity older = message(4L);
    MessageEntity extra = message(3L);
    when(memberClient.getMemberUserIds(501L)).thenReturn(List.of(100L, 200L));
    when(messageMapper.selectList(anyWrapper())).thenReturn(List.of(newest, older, extra));
    when(assembler.toPush(newest)).thenReturn(push(5L));
    when(assembler.toPush(older)).thenReturn(push(4L));

    MessagePage page = historyWithTenant(100L, 501L, null, 2);

    assertThat(page.hasMore()).isTrue();
    assertThat(page.messages()).extracting(MsgPush::getSeq).containsExactly(5L, 4L);
  }

  @Test
  void rejectsHistoryForNonMember() {
    when(memberClient.getMemberUserIds(501L)).thenReturn(List.of(200L));

    assertThatThrownBy(() -> historyWithTenant(100L, 501L, null, 20))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_CONV_MEMBER);
  }

  private SyncResp syncWithTenant(long userId, SyncReq request) {
    AtomicReference<SyncResp> result = new AtomicReference<>();
    TenantContext.runWithTenant(1L, () -> result.set(service.sync(userId, request)));
    return result.get();
  }

  private MessagePage historyWithTenant(long userId, long convId, Long endSeq, int limit) {
    AtomicReference<MessagePage> result = new AtomicReference<>();
    TenantContext.runWithTenant(1L, () -> result.set(service.history(userId, convId, endSeq, limit)));
    return result.get();
  }

  @SuppressWarnings("unchecked")
  private Wrapper<MessageEntity> anyWrapper() {
    return any(Wrapper.class);
  }

  private MessageEntity message(long seq) {
    MessageEntity message = new MessageEntity();
    message.setId(9000L + seq);
    message.setConversationId(501L);
    message.setSeq(seq);
    message.setSenderId(100L);
    message.setClientMsgId("client-" + seq);
    message.setContent(MsgContent.newBuilder()
        .setText(TextContent.newBuilder().setText("hello " + seq))
        .build()
        .toByteArray());
    message.setCreatedAt(LocalDateTime.of(2026, 6, 13, 0, 0).plusSeconds(seq));
    return message;
  }

  private MsgPush push(long seq) {
    return MsgPush.newBuilder()
        .setConvId(501L)
        .setSeq(seq)
        .setServerMsgId(9000L + seq)
        .build();
  }
}
