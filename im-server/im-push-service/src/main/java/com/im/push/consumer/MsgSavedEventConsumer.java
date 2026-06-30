package com.im.push.consumer;

import com.im.common.tenant.TenantContext;
import com.im.proto.common.ConvType;
import com.im.proto.events.MsgSavedEvent;
import com.im.proto.ws.Cmd;
import com.im.push.service.ConversationMemberClient;
import com.im.push.service.ConversationMemberClient.ConvMembersResult;
import com.im.push.service.OnlineAgentClient;
import com.im.push.service.PushDispatchService;
import com.im.push.service.PushEventDeduplicator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/**
 * 消息落库事件消费者：推送消息给会话成员（D33 增加 CS 会话路由）。
 *
 * <p>D33 CS 推送路由：
 * <ul>
 *   <li>open(1) — 推送给所有 online/busy 坐席 + 访客（open 状态需坐席知晓）</li>
 *   <li>assigned(2) — 只推送给绑定坐席 + 访客（缩小范围避免打扰其他坐席）</li>
 *   <li>resolved(3) — 仅推送给访客（会话已结单，坐席无需感知新消息）</li>
 * </ul>
 */
@Service
public class MsgSavedEventConsumer {

  private static final int CONV_TYPE_CS = ConvType.CS_SESSION.getNumber();  // 3
  private static final int CS_STATUS_OPEN     = 1;
  private static final int CS_STATUS_ASSIGNED = 2;

  private final ConversationMemberClient conversationMemberClient;
  private final OnlineAgentClient onlineAgentClient;
  private final PushDispatchService pushDispatchService;
  private final PushEventDeduplicator deduplicator;

  public MsgSavedEventConsumer(ConversationMemberClient conversationMemberClient,
      OnlineAgentClient onlineAgentClient,
      PushDispatchService pushDispatchService,
      PushEventDeduplicator deduplicator) {
    this.conversationMemberClient = conversationMemberClient;
    this.onlineAgentClient = onlineAgentClient;
    this.pushDispatchService = pushDispatchService;
    this.deduplicator = deduplicator;
  }

  @RabbitListener(queues = "${im.push.msg-saved-queue:im.push.msg.saved}")
  public void onMessage(byte[] payload,
      @Header(name = "event_id", required = false) Long eventId) throws Exception {
    MsgSavedEvent event = MsgSavedEvent.parseFrom(payload);
    long dedupId = eventId == null || eventId <= 0 ? event.getServerMsgId() : eventId;
    handle(dedupId, event);
  }

  void handle(long eventId, MsgSavedEvent event) {
    TenantContext.runWithTenant(event.getTenantId(), () -> {
      if (!deduplicator.tryMark(event.getTenantId(), eventId)) {
        return;
      }

      // 获取会话成员列表及 CS 路由元数据（D33）
      ConvMembersResult result = conversationMemberClient.getMembersResult(event.getConvId());
      List<Long> recipients = buildRecipients(result, event);

      // 排除发起连接：发送方其发起连接已通过 MSG_SEND_ACK 获知结果，无需再收自己消息的 MSG_PUSH 回显。
      // 仅排"发起连接"（excludeConnId），保留发送方其他端的实时多端同步；系统消息 sender_conn_id 为空串时不排除。
      pushDispatchService.pushToUsers(
          event.getTenantId(),
          recipients,
          Cmd.MSG_PUSH_VALUE,
          event.getPushReady().toByteArray(),
          true,
          event.getSenderId(),
          event.getSenderConnId());
    });
  }

  /**
   * 根据会话类型和 CS 状态构建推送接收方列表（D33）。
   *
   * <ul>
   *   <li>非 CS 会话：返回 conversation_member 成员（原逻辑）</li>
   *   <li>CS open：conversation_member（访客）+ 所有在线坐席</li>
   *   <li>CS assigned：conversation_member（访客）+ 绑定坐席</li>
   *   <li>CS resolved：仅 conversation_member（访客）</li>
   * </ul>
   */
  private List<Long> buildRecipients(ConvMembersResult result, MsgSavedEvent event) {
    if (result.convType() != CONV_TYPE_CS) {
      // 普通 C2C / GROUP 会话：直接用成员列表
      return result.userIds();
    }

    // CS 会话：先加入 conversation_member（访客）
    Set<Long> recipients = new LinkedHashSet<>(result.userIds());

    if (result.csStatus() == CS_STATUS_OPEN) {
      // open：推给所有在线坐席（D33）
      List<Long> agentIds = onlineAgentClient.getOnlineAgentIds();
      recipients.addAll(agentIds);
    } else if (result.csStatus() == CS_STATUS_ASSIGNED && result.agentId() > 0) {
      // assigned：只推绑定坐席（D33），排除发送者避免回显（sender 会收到 MSG_SEND_ACK）
      if (result.agentId() != event.getSenderId()) {
        recipients.add(result.agentId());
      }
    }
    // resolved (3)：仅访客，无需补充坐席

    return new ArrayList<>(recipients);
  }
}
