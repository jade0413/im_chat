package com.im.message.service;

import com.im.common.id.SnowflakeIdGenerator;
import com.im.message.dao.entity.MessageEntity;
import com.im.message.dao.entity.OutboxEntity;
import com.im.proto.body.ConvInfo;
import com.im.proto.body.MsgPush;
import com.im.proto.body.MsgSend;
import com.im.proto.body.Sender;
import com.im.proto.common.MsgContent;
import com.im.proto.common.MsgStatus;
import com.im.proto.rpc.ConnCtx;
import com.google.protobuf.InvalidProtocolBufferException;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MessageAssembler {

  public static final int MSG_TYPE_TEXT = 1;
  public static final int MSG_TYPE_IMAGE = 2;
  public static final int MSG_TYPE_VOICE = 3;
  public static final int MSG_TYPE_FILE = 4;
  public static final int MSG_TYPE_NOTIFICATION = 10;
  public static final int STATUS_NORMAL = 1;
  public static final int STATUS_REVOKED = 2;
  public static final int OUTBOX_PENDING = 0;
  public static final String EVENT_MSG_SAVED = MsgSavedEventFactory.EVENT_TYPE;

  private static final String EXT_STATUS = "status";
  private static final String EXT_REVOKE_REASON = "revoke_reason";
  private static final String EXT_MSG_TYPE = "msg_type";

  private static final int ABSTRACT_LIMIT = 255;

  private final SnowflakeIdGenerator idGenerator;
  private final Clock clock;

  @Autowired
  public MessageAssembler(SnowflakeIdGenerator idGenerator) {
    this(idGenerator, Clock.systemUTC());
  }

  MessageAssembler(SnowflakeIdGenerator idGenerator, Clock clock) {
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  public MessageEntity newTextMessage(ConnCtx ctx, MsgSend request, ConvInfo conv, long seq) {
    return newMessage(ctx, request, conv, seq);
  }

  public MessageEntity newMessage(ConnCtx ctx, MsgSend request, ConvInfo conv, long seq) {
    MsgContent content = request.getContent();
    LocalDateTime createdAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

    MessageEntity message = new MessageEntity();
    message.setId(idGenerator.nextId());
    message.setConversationId(conv.getConvId());
    message.setSeq(seq);
    message.setSenderId(ctx.getUserId());
    message.setClientMsgId(request.getClientMsgId());
    message.setMsgType(msgType(content));
    message.setContent(content.toByteArray());
    message.setAbstractText(abstractText(content));
    message.setStatus(STATUS_NORMAL);
    message.setCreatedAt(createdAt);
    return message;
  }

  public MsgPush toPush(ConnCtx ctx, MsgSend request, ConvInfo conv, MessageEntity message) {
    return MsgPush.newBuilder()
        .setConvId(message.getConversationId())
        .setConvType(conv.getType())
        .setSeq(message.getSeq())
        .setServerMsgId(message.getId())
        .setClientMsgId(message.getClientMsgId())
        .setSender(Sender.newBuilder().setUserId(ctx.getUserId()).build())
        .setSendTime(toEpochMillis(message.getCreatedAt()))
        .setContent(request.getContent())
        .putAllExt(request.getExtMap())
        .putExt(EXT_MSG_TYPE, Integer.toString(message.getMsgType()))
        .putExt(EXT_STATUS, Integer.toString(STATUS_NORMAL))
        .build();
  }

  public MsgPush toPush(MessageEntity message) {
    return toPush(message, com.im.proto.common.ConvType.C2C);
  }

  public MsgPush toPush(MessageEntity message, com.im.proto.common.ConvType convType) {
    MsgPush.Builder builder = MsgPush.newBuilder()
        .setConvId(message.getConversationId())
        .setConvType(convType == null ? com.im.proto.common.ConvType.CONV_TYPE_UNSPECIFIED : convType)
        .setSeq(message.getSeq())
        .setServerMsgId(message.getId())
        .setClientMsgId(nullToBlank(message.getClientMsgId()))
        .setSender(Sender.newBuilder().setUserId(message.getSenderId()).build())
        .setSendTime(toEpochMillis(message.getCreatedAt()))
        .putExt(EXT_MSG_TYPE, Integer.toString(msgType(message)))
        .putExt(EXT_STATUS, Integer.toString(status(message)));
    if (isRevoked(message)) {
      builder.putExt(EXT_REVOKE_REASON, Integer.toString(revokeReason(message)));
      return builder.build();
    }
    return builder.setContent(parseContent(message.getContent())).build();
  }

  public OutboxEntity msgSavedOutbox(long tenantId, MsgPush push) {
    OutboxEntity outbox = new OutboxEntity();
    outbox.setEventType(MsgSavedEventFactory.EVENT_TYPE);
    outbox.setRoutingKey(MsgSavedEventFactory.routingKey(tenantId));
    outbox.setPayload(MsgSavedEventFactory.toProto(tenantId, push).toByteArray());
    outbox.setStatus(OUTBOX_PENDING);
    outbox.setRetryCount(0);
    outbox.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    return outbox;
  }

  public MessageSendResult toResult(MessageEntity message) {
    return new MessageSendResult(
        message.getId(),
        message.getConversationId(),
        message.getSeq(),
        toEpochMillis(message.getCreatedAt()));
  }

  public String abstractText(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    String normalized = text.replaceAll("\\s+", " ").trim();
    if (normalized.length() <= ABSTRACT_LIMIT) {
      return normalized;
    }
    return normalized.substring(0, ABSTRACT_LIMIT);
  }

  private String abstractText(MsgContent content) {
    return switch (content.getContentCase()) {
      case TEXT -> abstractText(content.getText().getText());
      case IMAGE -> "[image]";
      case VOICE -> "[voice]";
      case FILE -> abstractText("[file] " + content.getFile().getFileName());
      case NOTIFICATION -> abstractText(content.getNotification().getEventType());
      case CUSTOM -> abstractText(content.getCustom().getCustomType());
      default -> "";
    };
  }

  private int msgType(MsgContent content) {
    return switch (content.getContentCase()) {
      case TEXT -> MSG_TYPE_TEXT;
      case IMAGE -> MSG_TYPE_IMAGE;
      case VOICE -> MSG_TYPE_VOICE;
      case FILE -> MSG_TYPE_FILE;
      case NOTIFICATION -> MSG_TYPE_NOTIFICATION;
      default -> 0;
    };
  }

  private int msgType(MessageEntity message) {
    return message.getMsgType() == null ? 0 : message.getMsgType();
  }

  private long toEpochMillis(LocalDateTime dateTime) {
    if (dateTime == null) {
      return clock.millis();
    }
    return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
  }

  private MsgContent parseContent(byte[] content) {
    try {
      return MsgContent.parseFrom(content == null ? new byte[0] : content);
    } catch (InvalidProtocolBufferException ex) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "stored message content is invalid", ex);
    }
  }

  private boolean isRevoked(MessageEntity message) {
    return status(message) == STATUS_REVOKED;
  }

  private int status(MessageEntity message) {
    Integer status = message.getStatus();
    if (status == null || status == MsgStatus.MSG_STATUS_UNSPECIFIED.getNumber()) {
      return STATUS_NORMAL;
    }
    return status;
  }

  private int revokeReason(MessageEntity message) {
    return message.getRevokeReason() == null ? 0 : message.getRevokeReason();
  }

  private String nullToBlank(String value) {
    return value == null ? "" : value;
  }
}
