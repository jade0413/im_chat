package com.im.message.service;

import com.im.proto.body.MsgPush;
import com.im.proto.events.MsgSavedEvent;
import org.springframework.stereotype.Service;

@Service
public class MsgSavedEventFactory {

  public static final String EVENT_TYPE = "msg.saved";

  public MsgSavedOutboxEvent create(long tenantId, MsgPush push) {
    return new MsgSavedOutboxEvent(
        EVENT_TYPE,
        routingKey(tenantId),
        toProto(tenantId, push).toByteArray());
  }

  public static String routingKey(long tenantId) {
    return EVENT_TYPE + "." + tenantId;
  }

  static MsgSavedEvent toProto(long tenantId, MsgPush push) {
    return MsgSavedEvent.newBuilder()
        .setTenantId(tenantId)
        .setConvId(push.getConvId())
        .setSeq(push.getSeq())
        .setServerMsgId(push.getServerMsgId())
        .setSenderId(push.getSender().getUserId())
        .setPushReady(push)
        .build();
  }

  public record MsgSavedOutboxEvent(String eventType, String routingKey, byte[] payload) {
  }
}
