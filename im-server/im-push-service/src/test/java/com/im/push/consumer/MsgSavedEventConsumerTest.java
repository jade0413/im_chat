package com.im.push.consumer;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.im.proto.common.FileContent;
import com.im.proto.common.ImageContent;
import com.im.proto.common.MsgContent;
import com.im.proto.common.TextContent;
import com.im.proto.common.VoiceContent;
import com.im.proto.body.MsgPush;
import com.im.proto.common.ConvType;
import com.im.proto.events.MsgSavedEvent;
import com.im.proto.ws.Cmd;
import com.im.push.config.PushProperties;
import com.im.push.service.ConversationMemberClient;
import com.im.push.service.ConversationMemberClient.ConvMembersResult;
import com.im.push.service.OnlineAgentClient;
import com.im.push.service.PushDispatchService;
import com.im.push.service.PushEventDeduplicator;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MsgSavedEventConsumerTest {

  @Mock
  private ConversationMemberClient conversationMemberClient;

  @Mock
  private OnlineAgentClient onlineAgentClient;

  @Mock
  private PushDispatchService pushDispatchService;

  @Mock
  private PushEventDeduplicator deduplicator;

  private MsgSavedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new MsgSavedEventConsumer(
        conversationMemberClient,
        onlineAgentClient,
        pushDispatchService,
        deduplicator,
        new PushProperties(Duration.ofSeconds(90), Duration.ofHours(24),
            "push.gw.", "im.push.msg.saved", "im.push.msg.revoked", 3));
  }

  @Test
  void pushesSavedMessageToConversationMembersOnce() {
    MsgSavedEvent event = event();
    when(deduplicator.tryMark(1L, 10L)).thenReturn(true);
    when(conversationMemberClient.getMembersResult(501L))
        .thenReturn(new ConvMembersResult(
            List.of(100L, 200L),
            ConvType.C2C.getNumber(),
            0,
            0L));

    consumer.handle(10L, event);

    verify(pushDispatchService).pushToUsers(
        1L,
        List.of(100L, 200L),
        Cmd.MSG_PUSH_VALUE,
        event.getPushReady().toByteArray(),
        true,
        100L,
        "conn-sender");
  }

  @Test
  void skipsDuplicateSavedEvent() {
    MsgSavedEvent event = event();
    when(deduplicator.tryMark(1L, 10L)).thenReturn(false);

    consumer.handle(10L, event);

    verify(conversationMemberClient, never()).getMemberUserIds(org.mockito.Mockito.anyLong());
    verify(conversationMemberClient, never()).getMembersResult(org.mockito.Mockito.anyLong());
    verify(pushDispatchService, never()).pushToUsers(
        org.mockito.Mockito.anyLong(),
        org.mockito.Mockito.anyCollection(),
        org.mockito.Mockito.anyInt(),
        org.mockito.Mockito.any(),
        org.mockito.Mockito.anyBoolean(),
        org.mockito.Mockito.anyLong(),
        org.mockito.Mockito.anyString());
  }

  @Test
  void sendsLightPushForLargeGroupMediaMessage() throws Exception {
    MsgSavedEvent event = eventWithContent(MsgContent.newBuilder()
        .setImage(ImageContent.newBuilder()
            .setObjectKey("1/202607/image")
            .setThumbKey("1/202607/image_thumb")
            .setMime("image/jpeg")
            .build())
        .build());
    when(deduplicator.tryMark(1L, 10L)).thenReturn(true);
    when(conversationMemberClient.getMembersResult(501L))
        .thenReturn(new ConvMembersResult(
            List.of(100L, 200L, 300L),
            ConvType.GROUP.getNumber(),
            0,
            0L));

    consumer.handle(10L, event);

    ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(pushDispatchService).pushToUsers(
        org.mockito.Mockito.eq(1L),
        org.mockito.Mockito.eq(List.of(100L, 200L, 300L)),
        org.mockito.Mockito.eq(Cmd.MSG_PUSH_VALUE),
        bodyCaptor.capture(),
        org.mockito.Mockito.eq(true),
        org.mockito.Mockito.eq(100L),
        org.mockito.Mockito.eq("conn-sender"));
    MsgPush pushed = MsgPush.parseFrom(bodyCaptor.getValue());
    org.assertj.core.api.Assertions.assertThat(pushed.getContentOmitted()).isTrue();
    org.assertj.core.api.Assertions.assertThat(pushed.getOmittedReason()).isEqualTo("large_group_media");
    org.assertj.core.api.Assertions.assertThat(pushed.hasContent()).isFalse();
    org.assertj.core.api.Assertions.assertThat(pushed.getExtMap()).doesNotContainKeys(
        "__push_mode", "__omitted_reason");
    org.assertj.core.api.Assertions.assertThat(pushed.getExtOrDefault("__abstract", "")).isEqualTo("[图片]");
  }

  @Test
  void sendsVoiceAbstractForLargeGroupVoiceMessage() throws Exception {
    MsgSavedEvent event = eventWithContent(MsgContent.newBuilder()
        .setVoice(VoiceContent.newBuilder()
            .setObjectKey("1/202607/voice")
            .setCodec("aac")
            .build())
        .build());
    when(deduplicator.tryMark(1L, 10L)).thenReturn(true);
    when(conversationMemberClient.getMembersResult(501L))
        .thenReturn(new ConvMembersResult(
            List.of(100L, 200L, 300L),
            ConvType.GROUP.getNumber(),
            0,
            0L));

    consumer.handle(10L, event);

    MsgPush pushed = capturedPush();
    org.assertj.core.api.Assertions.assertThat(pushed.getContentOmitted()).isTrue();
    org.assertj.core.api.Assertions.assertThat(pushed.getExtOrDefault("__abstract", "")).isEqualTo("[语音]");
  }

  @Test
  void sendsVideoAbstractForLargeGroupVideoFileCaseInsensitiveMime() throws Exception {
    MsgSavedEvent event = eventWithContent(MsgContent.newBuilder()
        .setFile(FileContent.newBuilder()
            .setObjectKey("1/202607/video")
            .setFileName("clip.mp4")
            .setMime("Video/MP4")
            .build())
        .build());
    when(deduplicator.tryMark(1L, 10L)).thenReturn(true);
    when(conversationMemberClient.getMembersResult(501L))
        .thenReturn(new ConvMembersResult(
            List.of(100L, 200L, 300L),
            ConvType.GROUP.getNumber(),
            0,
            0L));

    consumer.handle(10L, event);

    MsgPush pushed = capturedPush();
    org.assertj.core.api.Assertions.assertThat(pushed.getContentOmitted()).isTrue();
    org.assertj.core.api.Assertions.assertThat(pushed.getExtOrDefault("__abstract", "")).isEqualTo("[视频]");
  }

  @Test
  void sendsFileAbstractForLargeGroupDocumentMessage() throws Exception {
    MsgSavedEvent event = eventWithContent(MsgContent.newBuilder()
        .setFile(FileContent.newBuilder()
            .setObjectKey("1/202607/report")
            .setFileName("report.pdf")
            .setMime("application/pdf")
            .build())
        .build());
    when(deduplicator.tryMark(1L, 10L)).thenReturn(true);
    when(conversationMemberClient.getMembersResult(501L))
        .thenReturn(new ConvMembersResult(
            List.of(100L, 200L, 300L),
            ConvType.GROUP.getNumber(),
            0,
            0L));

    consumer.handle(10L, event);

    MsgPush pushed = capturedPush();
    org.assertj.core.api.Assertions.assertThat(pushed.getContentOmitted()).isTrue();
    org.assertj.core.api.Assertions.assertThat(pushed.getExtOrDefault("__abstract", "")).isEqualTo("[文件]");
  }

  @Test
  void keepsFullPushForLargeGroupTextMessage() {
    MsgSavedEvent event = eventWithContent(MsgContent.newBuilder()
        .setText(TextContent.newBuilder().setText("hello").build())
        .build());
    when(deduplicator.tryMark(1L, 10L)).thenReturn(true);
    when(conversationMemberClient.getMembersResult(501L))
        .thenReturn(new ConvMembersResult(
            List.of(100L, 200L, 300L),
            ConvType.GROUP.getNumber(),
            0,
            0L));

    consumer.handle(10L, event);

    verify(pushDispatchService).pushToUsers(
        1L,
        List.of(100L, 200L, 300L),
        Cmd.MSG_PUSH_VALUE,
        event.getPushReady().toByteArray(),
        true,
        100L,
        "conn-sender");
  }

  private MsgSavedEvent event() {
    return eventWithContent(null);
  }

  private MsgSavedEvent eventWithContent(MsgContent content) {
    MsgPush.Builder push = MsgPush.newBuilder()
        .setConvId(501L)
        .setSeq(1L)
        .setServerMsgId(9001L);
    if (content != null) {
      push.setContent(content);
    }
    return MsgSavedEvent.newBuilder()
        .setTenantId(1L)
        .setConvId(501L)
        .setSeq(1L)
        .setServerMsgId(9001L)
        .setSenderId(100L)
        .setSenderConnId("conn-sender")
        .setPushReady(push)
        .build();
  }

  private MsgPush capturedPush() throws Exception {
    ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(pushDispatchService).pushToUsers(
        org.mockito.Mockito.eq(1L),
        org.mockito.Mockito.eq(List.of(100L, 200L, 300L)),
        org.mockito.Mockito.eq(Cmd.MSG_PUSH_VALUE),
        bodyCaptor.capture(),
        org.mockito.Mockito.eq(true),
        org.mockito.Mockito.eq(100L),
        org.mockito.Mockito.eq("conn-sender"));
    return MsgPush.parseFrom(bodyCaptor.getValue());
  }
}
