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
import com.im.message.dao.mapper.ConversationProgressMapper;
import com.im.message.dao.mapper.MessageMapper;
import com.im.proto.body.ConvInfo;
import com.im.proto.body.MsgPush;
import com.im.proto.body.SyncReq;
import com.im.proto.body.SyncResp;
import com.im.proto.common.ConvType;
import com.im.proto.common.MsgContent;
import com.im.proto.common.TextContent;
import com.im.proto.rpc.PullMsgsReq;
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
  private ConversationProgressMapper conversationProgressMapper;

  @Mock
  private MessageAssembler assembler;

  @Mock
  private ConversationMemberClient memberClient;

  private MessageQueryService service;

  @BeforeEach
  void setUp() {
    service = new MessageQueryService(messageMapper, conversationProgressMapper, assembler, memberClient);
  }

  @Test
  void syncReturnsGapMessagesAndHasMore() {
    MessageEntity first = message(2L);
    MessageEntity second = message(3L);
    when(memberClient.listMemberConvs(100L, 10L))
        .thenReturn(new ConversationListPage(List.of(), false, 10L));
    when(memberClient.getMemberConv(100L, 501L)).thenReturn(ConvInfo.newBuilder()
        .setConvId(501L)
        .setType(ConvType.C2C)
        .setPeerUserId(200L)
        .setMaxSeq(3L)
        .setReadSeq(1L)
        .build());
    when(messageMapper.selectList(anyWrapper())).thenReturn(List.of(first, second));
    when(assembler.toPush(first, ConvType.C2C)).thenReturn(push(2L));
    when(assembler.toPush(second, ConvType.C2C)).thenReturn(push(3L));

    SyncResp response = syncWithTenant(100L, SyncReq.newBuilder()
        .setConvListVersion(10L)
        .addConvVersions(SyncReq.ConvVersion.newBuilder()
            .setConvId(501L)
            .setLocalMaxSeq(1L))
        .build());

    assertThat(response.getDeltasList()).hasSize(1);
    assertThat(response.getDeltas(0).getConv().getReadSeq()).isEqualTo(1L);
    assertThat(response.getDeltas(0).getServerMaxSeq()).isEqualTo(3L);
    assertThat(response.getDeltas(0).getMsgsList()).extracting(MsgPush::getSeq)
        .containsExactly(2L, 3L);
    assertThat(response.getDeltas(0).getHasMore()).isFalse();
  }

  @Test
  void syncEmptyConvVersionsReturnsMemberConversations() {
    MessageEntity first = message(1L);
    MessageEntity second = message(2L);
    when(memberClient.listMemberConvs(100L, 0L)).thenReturn(new ConversationListPage(List.of(ConvInfo.newBuilder()
        .setConvId(501L)
        .setType(ConvType.C2C)
        .setPeerUserId(200L)
        .setMaxSeq(2L)
        .setReadSeq(1L)
        .build()), false, 7L));
    when(messageMapper.selectList(anyWrapper())).thenReturn(List.of(first, second));
    when(assembler.toPush(first, ConvType.C2C)).thenReturn(push(1L));
    when(assembler.toPush(second, ConvType.C2C)).thenReturn(push(2L));

    SyncResp response = syncWithTenant(100L, SyncReq.getDefaultInstance());

    assertThat(response.getDeltasList()).hasSize(1);
    assertThat(response.getDeltas(0).getConv().getConvId()).isEqualTo(501L);
    assertThat(response.getDeltas(0).getConv().getReadSeq()).isEqualTo(1L);
    assertThat(response.getDeltas(0).getServerMaxSeq()).isEqualTo(2L);
    assertThat(response.getDeltas(0).getMsgsList()).extracting(MsgPush::getSeq)
        .containsExactly(1L, 2L);
    assertThat(response.getConvListVersion()).isEqualTo(7L);
  }

  @Test
  void syncReturnsConversationListDiffWithoutMessageGap() {
    when(memberClient.listMemberConvs(100L, 3L)).thenReturn(new ConversationListPage(List.of(
        ConvInfo.newBuilder()
            .setConvId(501L)
            .setType(ConvType.C2C)
            .setMaxSeq(5L)
            .setReadSeq(4L)
            .setPinned(true)
            .build()), false, 4L));

    SyncResp response = syncWithTenant(100L, SyncReq.newBuilder()
        .setConvListVersion(3L)
        .addConvVersions(SyncReq.ConvVersion.newBuilder()
            .setConvId(501L)
            .setLocalMaxSeq(5L))
        .build());

    assertThat(response.getConvListVersion()).isEqualTo(4L);
    assertThat(response.getDeltasList()).hasSize(1);
    assertThat(response.getDeltas(0).getConv().getPinned()).isTrue();
    assertThat(response.getDeltas(0).getMsgsList()).isEmpty();
    assertThat(response.getDeltas(0).getServerMaxSeq()).isEqualTo(5L);
  }

  @Test
  void syncReturnsDeletedConversationDiff() {
    when(memberClient.listMemberConvs(100L, 3L)).thenReturn(new ConversationListPage(List.of(
        ConvInfo.newBuilder()
            .setConvId(501L)
            .setType(ConvType.C2C)
            .setDeleted(true)
            .build()), false, 4L));

    SyncResp response = syncWithTenant(100L, SyncReq.newBuilder()
        .setConvListVersion(3L)
        .addConvVersions(SyncReq.ConvVersion.newBuilder()
            .setConvId(501L)
            .setLocalMaxSeq(5L))
        .build());

    assertThat(response.getConvListVersion()).isEqualTo(4L);
    assertThat(response.getDeltasList()).hasSize(1);
    assertThat(response.getDeltas(0).getConv().getDeleted()).isTrue();
    assertThat(response.getDeltas(0).getMsgsList()).isEmpty();
  }

  @Test
  void syncRequestsFullSyncWhenConversationEventDiffIsTooLarge() {
    when(memberClient.listMemberConvs(100L, 3L))
        .thenReturn(new ConversationListPage(List.of(), true, 9L));

    SyncResp response = syncWithTenant(100L, SyncReq.newBuilder()
        .setConvListVersion(3L)
        .build());

    assertThat(response.getFullSync()).isTrue();
    assertThat(response.getConvListVersion()).isEqualTo(9L);
  }

  @Test
  void rejectsSyncWithTooManyConversationVersions() {
    SyncReq.Builder request = SyncReq.newBuilder().setConvListVersion(1L);
    for (int i = 0; i < 501; i++) {
      request.addConvVersions(SyncReq.ConvVersion.newBuilder()
          .setConvId(1000L + i)
          .setLocalMaxSeq(1L));
    }

    assertThatThrownBy(() -> syncWithTenant(100L, request.build()))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void historyReturnsDescendingPageAndHasMore() {
    MessageEntity newest = message(5L);
    MessageEntity older = message(4L);
    MessageEntity extra = message(3L);
    when(memberClient.getMemberConv(100L, 501L)).thenReturn(ConvInfo.newBuilder()
        .setConvId(501L)
        .setMaxSeq(5L)
        .setReadSeq(4L)
        .build());
    when(messageMapper.selectList(anyWrapper())).thenReturn(List.of(newest, older, extra));
    when(assembler.toPush(newest, ConvType.CONV_TYPE_UNSPECIFIED)).thenReturn(push(5L));
    when(assembler.toPush(older, ConvType.CONV_TYPE_UNSPECIFIED)).thenReturn(push(4L));

    MessagePage page = historyWithTenant(100L, 501L, null, 2);

    assertThat(page.hasMore()).isTrue();
    assertThat(page.readSeq()).isEqualTo(4L);
    assertThat(page.messages()).extracting(MsgPush::getSeq).containsExactly(5L, 4L);
  }

  @Test
  void rejectsHistoryForNonMember() {
    when(memberClient.getMemberConv(100L, 501L)).thenThrow(new ImException(ErrorCode.NOT_CONV_MEMBER));

    assertThatThrownBy(() -> historyWithTenant(100L, 501L, null, 20))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_CONV_MEMBER);
  }

  @Test
  void pullForRpcUsesConversationTypeWhenAssemblingMessages() {
    MessageEntity first = message(1L);
    when(conversationProgressMapper.selectType(501L)).thenReturn(ConvType.GROUP.getNumber());
    when(messageMapper.selectList(anyWrapper())).thenReturn(List.of(first));
    when(assembler.toPush(first, ConvType.GROUP)).thenReturn(push(1L).toBuilder()
        .setConvType(ConvType.GROUP)
        .build());

    List<MsgPush> messages = pullRpcWithTenant(PullMsgsReq.newBuilder()
        .setConvId(501L)
        .setBeginSeq(1L)
        .setEndSeq(10L)
        .setLimit(20)
        .build());

    assertThat(messages).hasSize(1);
    assertThat(messages.getFirst().getConvType()).isEqualTo(ConvType.GROUP);
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

  private List<MsgPush> pullRpcWithTenant(PullMsgsReq request) {
    AtomicReference<List<MsgPush>> result = new AtomicReference<>();
    TenantContext.runWithTenant(1L, () -> result.set(service.pullForRpc(request)));
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
