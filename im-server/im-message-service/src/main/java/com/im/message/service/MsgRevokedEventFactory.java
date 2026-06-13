package com.im.message.service;

import com.im.proto.events.MsgRevokedEvent;
import org.springframework.stereotype.Service;

@Service
public class MsgRevokedEventFactory {

  public static final String EVENT_TYPE = "msg.revoked";

  public MsgRevokedOutboxEvent create(long tenantId, long conversationId, long seq,
      long serverMsgId, int reason, long operatorUserId) {
    return new MsgRevokedOutboxEvent(
        EVENT_TYPE,
        routingKey(tenantId),
        toProto(tenantId, conversationId, seq, serverMsgId, reason, operatorUserId).toByteArray());
  }

  public static String routingKey(long tenantId) {
    return EVENT_TYPE + "." + tenantId;
  }

  static MsgRevokedEvent toProto(long tenantId, long conversationId, long seq,
      long serverMsgId, int reason, long operatorUserId) {
    return MsgRevokedEvent.newBuilder()
        .setTenantId(tenantId)
        .setConvId(conversationId)
        .setSeq(seq)
        .setServerMsgId(serverMsgId)
        .setReason(reason)
        .setOperatorUserId(operatorUserId)
        .build();
  }

  public record MsgRevokedOutboxEvent(String eventType, String routingKey, byte[] payload) {
  }
}
