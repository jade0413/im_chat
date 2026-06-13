package com.im.push.service;

import java.util.List;

public interface ConversationMemberClient {

  List<Long> getMemberUserIds(long conversationId);

  /**
   * 返回成员列表及 CS 推送路由元数据（D33）。
   * 非 CS 会话的 csStatus / agentId 为 0。
   */
  ConvMembersResult getMembersResult(long conversationId);

  /** 成员列表 + CS 路由元数据。 */
  record ConvMembersResult(
      List<Long> userIds,
      int convType,
      int csStatus,
      long agentId
  ) {}
}
