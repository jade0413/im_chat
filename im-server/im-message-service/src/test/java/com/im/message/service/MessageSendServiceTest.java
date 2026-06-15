package com.im.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.message.dao.entity.MessageEntity;
import com.im.proto.body.ConvInfo;
import com.im.proto.body.MsgSend;
import com.im.proto.common.ConvType;
import com.im.proto.common.CustomContent;
import com.im.proto.common.ImageContent;
import com.im.proto.common.MsgContent;
import com.im.proto.common.TextContent;
import com.im.proto.rpc.ConnCtx;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class MessageSendServiceTest {

  @Mock
  private MessageIdempotencyService idempotencyService;

  @Mock
  private ConversationResolver conversationResolver;

  @Mock
  private UserRelationClient relationClient;

  @Mock
  private MessagePersistService persistService;

  @Mock
  private MessageAssembler assembler;

  @Mock
  private MessageFileReferenceValidator fileReferenceValidator;

  private MessageSendService service;

  @BeforeEach
  void setUp() {
    service = new MessageSendService(
        idempotencyService,
        conversationResolver,
        relationClient,
        persistService,
        assembler,
        fileReferenceValidator);
  }

  @Test
  void returnsExistingResultWithoutAllocatingSeq() {
    MessageEntity existing = existingMessage();
    MessageSendResult expected = new MessageSendResult(9001L, 501L, 3L, 1000L);
    when(idempotencyService.findExisting("client-1")).thenReturn(existing);
    when(assembler.toResult(existing)).thenReturn(expected);

    MessageSendResult result = sendWithTenant(request());

    assertThat(result).isSameAs(expected);
    verify(conversationResolver, never()).resolve(ctx(), request());
    verify(persistService, never()).persist(1L, ctx(), request(), conv());
  }

  @Test
  void sendsNewTextMessage() {
    MessageSendResult expected = new MessageSendResult(9002L, 501L, 4L, 1000L);
    when(idempotencyService.findExisting("client-1")).thenReturn(null);
    when(idempotencyService.tryAcquire(1L, "client-1")).thenReturn(true);
    when(conversationResolver.resolve(ctx(), request())).thenReturn(conv());
    when(persistService.persist(1L, ctx(), request(), conv())).thenReturn(expected);

    MessageSendResult result = sendWithTenant(request());

    assertThat(result).isSameAs(expected);
  }

  @Test
  void rejectsWhenBlockedByPeer() {
    when(idempotencyService.findExisting("client-1")).thenReturn(null);
    when(idempotencyService.tryAcquire(1L, "client-1")).thenReturn(true);
    org.mockito.Mockito.doThrow(new ImException(ErrorCode.BLOCKED_BY_PEER))
        .when(relationClient).ensureCanSendC2c(100L, 200L);

    assertThatThrownBy(() -> sendWithTenant(request()))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BLOCKED_BY_PEER);
    verify(conversationResolver, never()).resolve(ctx(), request());
  }

  @Test
  void waitsForInFlightDuplicateWithoutAllocatingSeq() {
    MessageEntity existing = existingMessage();
    MessageSendResult expected = new MessageSendResult(9001L, 501L, 3L, 1000L);
    when(idempotencyService.findExisting("client-1")).thenReturn(null);
    when(idempotencyService.tryAcquire(1L, "client-1")).thenReturn(false);
    when(idempotencyService.waitForExisting("client-1")).thenReturn(existing);
    when(assembler.toResult(existing)).thenReturn(expected);

    MessageSendResult result = sendWithTenant(request());

    assertThat(result).isSameAs(expected);
    verify(persistService, never()).persist(1L, ctx(), request(), conv());
  }

  @Test
  void duplicateDbInsertReturnsOriginalMessage() {
    MessageEntity existing = existingMessage();
    MessageSendResult expected = new MessageSendResult(9001L, 501L, 3L, 1000L);
    when(idempotencyService.findExisting("client-1")).thenReturn(null, existing);
    when(idempotencyService.tryAcquire(1L, "client-1")).thenReturn(true);
    when(conversationResolver.resolve(ctx(), request())).thenReturn(conv());
    when(persistService.persist(1L, ctx(), request(), conv()))
        .thenThrow(new DuplicateKeyException("uk_client_msg"));
    when(assembler.toResult(existing)).thenReturn(expected);

    MessageSendResult result = sendWithTenant(request());

    assertThat(result).isSameAs(expected);
  }

  @Test
  void sendsNewImageMessageAfterFileReferenceValidation() {
    MsgSend imageRequest = MsgSend.newBuilder()
        .setClientMsgId("client-1")
        .setToUserId(200L)
        .setContent(MsgContent.newBuilder()
            .setImage(ImageContent.newBuilder()
                .setObjectKey("1/202606/a.png")
                .setMime("image/png")
                .setSize(512L)))
        .build();
    MessageSendResult expected = new MessageSendResult(9002L, 501L, 4L, 1000L);
    when(idempotencyService.findExisting("client-1")).thenReturn(null);
    when(idempotencyService.tryAcquire(1L, "client-1")).thenReturn(true);
    when(conversationResolver.resolve(ctx(), imageRequest)).thenReturn(conv());
    when(persistService.persist(1L, ctx(), imageRequest, conv())).thenReturn(expected);

    MessageSendResult result = sendWithTenant(imageRequest);

    assertThat(result).isSameAs(expected);
    verify(fileReferenceValidator).ensureReferencesConfirmed(1L, imageRequest.getContent());
  }

  @Test
  void sendsCustomContentMessage() {
    MsgSend request = MsgSend.newBuilder()
        .setClientMsgId("client-1")
        .setToUserId(200L)
        .setContent(MsgContent.newBuilder()
            .setCustom(CustomContent.newBuilder().setCustomType("x").setPayload("{}")))
        .build();
    MessageSendResult expected = new MessageSendResult(9002L, 501L, 4L, 1000L);
    when(idempotencyService.findExisting("client-1")).thenReturn(null);
    when(idempotencyService.tryAcquire(1L, "client-1")).thenReturn(true);
    when(conversationResolver.resolve(ctx(), request)).thenReturn(conv());
    when(persistService.persist(1L, ctx(), request, conv())).thenReturn(expected);

    MessageSendResult result = sendWithTenant(request);

    assertThat(result).isSameAs(expected);
  }

  private MessageSendResult sendWithTenant(MsgSend request) {
    AtomicReference<MessageSendResult> result = new AtomicReference<>();
    TenantContext.runWithTenant(1L, () -> result.set(service.send(ctx(), request)));
    return result.get();
  }

  private ConnCtx ctx() {
    return ConnCtx.newBuilder().setTenantId(1L).setUserId(100L).build();
  }

  private MsgSend request() {
    return MsgSend.newBuilder()
        .setClientMsgId("client-1")
        .setToUserId(200L)
        .setContent(MsgContent.newBuilder()
            .setText(TextContent.newBuilder().setText("hello")))
        .build();
  }

  private ConvInfo conv() {
    return ConvInfo.newBuilder().setConvId(501L).setType(ConvType.C2C).build();
  }

  private MessageEntity existingMessage() {
    MessageEntity message = new MessageEntity();
    message.setId(9001L);
    message.setConversationId(501L);
    message.setSeq(3L);
    message.setCreatedAt(LocalDateTime.of(2026, 6, 13, 0, 0));
    return message;
  }
}
