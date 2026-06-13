package com.im.message.service;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.outbox.OutboxWriter;
import com.im.message.dao.entity.MessageEntity;
import com.im.message.dao.mapper.ConversationProgressMapper;
import com.im.message.dao.mapper.MessageMapper;
import com.im.proto.body.ConvInfo;
import com.im.proto.body.MsgPush;
import com.im.proto.body.MsgSend;
import com.im.proto.rpc.ConnCtx;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessagePersistService {

  private final MessageMapper messageMapper;
  private final ConversationProgressMapper conversationProgressMapper;
  private final SequenceService sequenceService;
  private final OutboxWriter outboxWriter;
  private final MessageAssembler assembler;
  private final MsgSavedEventFactory msgSavedEventFactory;

  public MessagePersistService(MessageMapper messageMapper,
      ConversationProgressMapper conversationProgressMapper,
      SequenceService sequenceService,
      OutboxWriter outboxWriter,
      MessageAssembler assembler,
      MsgSavedEventFactory msgSavedEventFactory) {
    this.messageMapper = messageMapper;
    this.conversationProgressMapper = conversationProgressMapper;
    this.sequenceService = sequenceService;
    this.outboxWriter = outboxWriter;
    this.assembler = assembler;
    this.msgSavedEventFactory = msgSavedEventFactory;
  }

  @Transactional
  public MessageSendResult persist(long tenantId, ConnCtx ctx, MsgSend request, ConvInfo conv) {
    long seq = sequenceService.nextSeq(conv.getConvId());
    MessageEntity message = assembler.newMessage(ctx, request, conv, seq);
    messageMapper.insert(message);

    int updated = conversationProgressMapper.updateLastMessage(
        message.getConversationId(),
        message.getSeq(),
        message.getAbstractText(),
        message.getCreatedAt());
    if (updated != 1) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "conversation progress update failed");
    }

    MsgPush push = assembler.toPush(ctx, request, conv, message);
    MsgSavedEventFactory.MsgSavedOutboxEvent event = msgSavedEventFactory.create(tenantId, push);
    outboxWriter.write(tenantId, event.eventType(), event.routingKey(), event.payload());
    return assembler.toResult(message);
  }
}
