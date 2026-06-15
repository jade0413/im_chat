package com.im.user.friend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MVP 占位实现：仅记日志，不真正下发实时通知。
 * T38 接入 gRPC（{@code MessageRpc.SendSystemNotification}）后，
 * 真实实现请标注 {@code @Primary} 覆盖本默认实现。
 */
@Component
public class LoggingFriendNotificationPort implements FriendNotificationPort {

  private static final Logger log = LoggerFactory.getLogger(LoggingFriendNotificationPort.class);

  @Override
  public void send(long tenantId, long toUserId, String eventType, String payloadJson) {
    log.info("[friend-notify-stub] tenant={} to={} event={} payload={} "
            + "(占位实现，未实时下发；接收方可经 GET /api/friend/requests 拉取)",
        tenantId, toUserId, eventType, payloadJson);
  }
}
