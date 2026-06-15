package com.im.cs.notify;

import com.im.cs.config.CsGrpcMetadata;
import com.im.cs.widget.CsConstants;
import com.im.proto.rpc.ConversationRpcGrpc;
import com.im.proto.rpc.ListAgentCsConvsReq;
import com.im.proto.rpc.ListAgentCsConvsResp;
import com.im.proto.rpc.MessageRpcGrpc;
import com.im.proto.rpc.SendSystemNotificationReq;
import com.im.common.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 坐席上线待处理提醒（T35，离线留言收口）。
 *
 * <p>坐席从 offline 切到 online 时调用：统计该坐席工作台里 24h 内有新消息的
 * <b>open 未认领</b>会话数，若有则向坐席下发一条系统通知（event_type=cs.pending），
 * 复用 D40 的 {@code SendSystemNotification} → 进坐席 SYSTEM 会话，App 内可见。
 * 无待处理则不打扰。状态真相在会话表，通知失败仅记日志、不影响上线。
 */
@Service
public class PendingConvNotifier {

  private static final Logger log = LoggerFactory.getLogger(PendingConvNotifier.class);
  private static final String EVENT_CS_PENDING = "cs.pending";
  private static final long ACTIVE_WINDOW_MS = 24L * 60 * 60 * 1000;
  private static final int SCAN_LIMIT = 50;

  private final ConversationRpcGrpc.ConversationRpcBlockingStub conversationStub;
  private final MessageRpcGrpc.MessageRpcBlockingStub messageStub;

  public PendingConvNotifier(
      @Qualifier("csConversationRpcBlockingStub")
      ConversationRpcGrpc.ConversationRpcBlockingStub conversationStub,
      @Qualifier("csMessageRpcBlockingStub")
      MessageRpcGrpc.MessageRpcBlockingStub messageStub) {
    this.conversationStub = conversationStub;
    this.messageStub = messageStub;
  }

  /** 坐席上线后调用：有待接待会话则发系统提醒。失败不抛出。 */
  public void notifyIfPending(long agentId) {
    try {
      long tenantId = TenantContext.requiredTenantId();
      ListAgentCsConvsResp resp = CsGrpcMetadata.withMetadata(conversationStub).listAgentCsConvs(
          ListAgentCsConvsReq.newBuilder()
              .setAgentId(agentId)
              .setLimit(SCAN_LIMIT)
              .setOffset(0)
              .build());

      long cutoff = System.currentTimeMillis() - ACTIVE_WINDOW_MS;
      long pending = resp.getConvsList().stream()
          .filter(c -> c.getCsStatus() == CsConstants.CS_STATUS_OPEN)   // open 未认领
          .filter(c -> c.getLastMsgTimeMs() >= cutoff)                  // 24h 内有活动
          .count();
      if (pending <= 0) {
        return; // 无待处理，不打扰
      }
      boolean hasMore = resp.getHasMore();
      String payload = "{\"count\":" + pending + ",\"has_more\":" + hasMore + "}";
      CsGrpcMetadata.withMetadata(messageStub).sendSystemNotification(
          SendSystemNotificationReq.newBuilder()
              .setTenantId(tenantId)
              .setToUserId(agentId)
              .setEventType(EVENT_CS_PENDING)
              .setPayload(payload)
              .build());
    } catch (Exception ex) {
      log.warn("[cs-pending-notify] agent={} failed: {}", agentId, ex.toString());
    }
  }
}
