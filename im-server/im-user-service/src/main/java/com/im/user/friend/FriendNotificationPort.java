package com.im.user.friend;

/**
 * 好友系统通知出口（D40）。把一条好友事件写入接收方 SYSTEM 会话，
 * 复用消息管道获得 seq / 多端同步 / 离线增量（见 friend-service-design.md §5）。
 *
 * <p>MVP 默认实现 {@link LoggingFriendNotificationPort} 仅记日志；
 * 生产实现应通过 in-process gRPC 调 {@code MessageRpc.SendSystemNotification}
 * （T38）。状态唯一真相在 friend_request 表，因此通知失败不影响关系正确性，
 * 客户端仍可经 {@code GET /api/friend/requests} 拉到最新状态。
 */
public interface FriendNotificationPort {

  String EVENT_FRIEND_REQUEST = "friend.request";
  String EVENT_FRIEND_ACCEPTED = "friend.accepted";
  String EVENT_FRIEND_ADDED = "friend.added";

  /**
   * @param tenantId   租户
   * @param toUserId   通知接收方
   * @param eventType  事件子类型（EVENT_* 常量）
   * @param payloadJson 事件体 JSON（只带 request_id 等展示字段，不带可变状态）
   */
  void send(long tenantId, long toUserId, String eventType, String payloadJson);
}
