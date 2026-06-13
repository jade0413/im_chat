package com.im.message.service;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.message.dao.entity.MessageEntity;
import com.im.proto.body.ConvInfo;
import com.im.proto.body.MsgSend;
import com.im.proto.common.ConvType;
import com.im.proto.common.MsgContent;
import com.im.proto.rpc.ConnCtx;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class MessageSendService {

  private static final int CLIENT_MSG_ID_LIMIT = 64;
  private static final int CONTENT_BYTES_LIMIT = 8192;

  private final MessageIdempotencyService idempotencyService;
  private final ConversationResolver conversationResolver;
  private final UserRelationClient relationClient;
  private final MessagePersistService persistService;
  private final MessageAssembler assembler;

  public MessageSendService(MessageIdempotencyService idempotencyService,
      ConversationResolver conversationResolver,
      UserRelationClient relationClient,
      MessagePersistService persistService,
      MessageAssembler assembler) {
    this.idempotencyService = idempotencyService;
    this.conversationResolver = conversationResolver;
    this.relationClient = relationClient;
    this.persistService = persistService;
    this.assembler = assembler;
  }

  public MessageSendResult send(ConnCtx ctx, MsgSend request) {
    long tenantId = validateContext(ctx);
    validateRequest(request);

    MessageEntity existing = idempotencyService.findExisting(request.getClientMsgId());
    if (existing != null) {
      return assembler.toResult(existing);
    }
    if (!idempotencyService.tryAcquire(tenantId, request.getClientMsgId())) {
      return assembler.toResult(idempotencyService.waitForExisting(request.getClientMsgId()));
    }

    if (request.getTargetCase() == MsgSend.TargetCase.TO_USER_ID) {
      relationClient.ensureCanSendC2c(ctx.getUserId(), request.getToUserId());
    }
    ConvInfo conv = conversationResolver.resolve(ctx, request);
    if (request.getTargetCase() == MsgSend.TargetCase.CONV_ID && conv.getType() == ConvType.C2C) {
      relationClient.ensureCanSendC2c(ctx.getUserId(), conv.getPeerUserId());
    }
    try {
      return persistService.persist(tenantId, ctx, request, conv);
    } catch (DuplicateKeyException ex) {
      MessageEntity duplicated = idempotencyService.findExisting(request.getClientMsgId());
      if (duplicated != null) {
        return assembler.toResult(duplicated);
      }
      throw new ImException(ErrorCode.INTERNAL_ERROR, "duplicated message but row not found", ex);
    }
  }

  private long validateContext(ConnCtx ctx) {
    long tenantId = TenantContext.requiredTenantId();
    if (ctx == null || ctx.getTenantId() <= 0 || ctx.getUserId() <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "valid connection context is required");
    }
    if (ctx.getTenantId() != tenantId) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "tenant mismatch");
    }
    return tenantId;
  }

  private void validateRequest(MsgSend request) {
    if (request.getClientMsgId() == null || request.getClientMsgId().isBlank()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "client_msg_id is required");
    }
    if (request.getClientMsgId().length() > CLIENT_MSG_ID_LIMIT) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "client_msg_id is too long");
    }
    if (request.getTargetCase() == MsgSend.TargetCase.TARGET_NOT_SET) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "message target is required");
    }
    if (!request.hasContent()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "message content is required");
    }
    MsgContent content = request.getContent();
    if (content.getContentCase() != MsgContent.ContentCase.TEXT) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "only text message is supported");
    }
    if (content.getText().getText() == null || content.getText().getText().isBlank()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "text message is empty");
    }
    if (content.toByteArray().length > CONTENT_BYTES_LIMIT) {
      throw new ImException(ErrorCode.MSG_TOO_LARGE);
    }
  }
}
