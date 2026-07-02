package com.im.push.consumer;

import com.im.common.tenant.TenantContext;
import com.im.proto.body.RevokeNotify;
import com.im.proto.common.RevokeReason;
import com.im.proto.events.MsgRevokedEvent;
import com.im.proto.ws.Cmd;
import com.im.push.service.ConversationMemberClient;
import com.im.push.service.PushDispatchService;
import com.im.push.service.PushEventDeduplicator;
import java.util.List;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
public class MsgRevokedEventConsumer {

  private final ConversationMemberClient conversationMemberClient;
  private final PushDispatchService pushDispatchService;
  private final PushEventDeduplicator deduplicator;

  public MsgRevokedEventConsumer(ConversationMemberClient conversationMemberClient,
      PushDispatchService pushDispatchService,
      PushEventDeduplicator deduplicator) {
    this.conversationMemberClient = conversationMemberClient;
    this.pushDispatchService = pushDispatchService;
    this.deduplicator = deduplicator;
  }

  @RabbitListener(queues = "${im.push.msg-revoked-queue:im.push.msg.revoked}")
  public void onMessage(byte[] payload,
      @Header(name = "event_id", required = false) Long eventId) throws Exception {
    MsgRevokedEvent event = MsgRevokedEvent.parseFrom(payload);
    long dedupId = eventId == null || eventId <= 0 ? event.getServerMsgId() : eventId;
    handle(dedupId, event);
  }

  void handle(long eventId, MsgRevokedEvent event) {
    TenantContext.runWithTenant(event.getTenantId(), () -> {
      if (!deduplicator.tryMark(event.getTenantId(), eventId)) {
        return;
      }
      List<Long> memberUserIds = conversationMemberClient.getMemberUserIds(event.getConvId());
      RevokeNotify notify = RevokeNotify.newBuilder()
          .setConvId(event.getConvId())
          .setSeq(event.getSeq())
          .setServerMsgId(event.getServerMsgId())
          .setReason(reason(event.getReason()))
          .setOperatorUserId(event.getOperatorUserId())
          .build();
      // 2026-07-02 客户端联调审查修正：协议 §3 约定 need_ack=true 仅用于 MSG_PUSH。
      // 此前误传 true——客户端只对 MSG_PUSH 回 MSG_RECV_ACK，REVOKE_NOTIFY 带非 0
      // req_id 却无人 ack，在线端收到撤回 10s 后被网关判半死链踢断。
      // 撤回通知不需送达跟踪：漏收由重连 SYNC / 历史拉取兜底（status=REVOKED）。
      pushDispatchService.pushToUsers(
          event.getTenantId(),
          memberUserIds,
          Cmd.REVOKE_NOTIFY_VALUE,
          notify.toByteArray(),
          false);
    });
  }

  private RevokeReason reason(int value) {
    RevokeReason reason = RevokeReason.forNumber(value);
    return reason == null ? RevokeReason.REVOKE_REASON_UNSPECIFIED : reason;
  }
}
