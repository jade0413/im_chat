package com.im.cs.agent.dto;

/**
 * 坐席工作台单个 CS 会话卡片（T35）。
 *
 * @param convId           会话 ID
 * @param csStatus         1=open（待接待）2=assigned（处理中）
 * @param agentId          当前绑定坐席 ID，0 表示未分配
 * @param visitorUserId    访客 user_id
 * @param visitorName      访客显示名（"访客XXXX"）
 * @param visitorOnline    访客当前是否有在线连接
 * @param visitorReadSeq   访客已读到的会话 seq
 * @param lastMsgTimeMs    最新消息时间戳（毫秒），0 表示无消息
 * @param lastMsgAbstract  最新消息摘要
 * @param maxSeq           会话最大 seq
 */
public record CsConvItemResponse(
    long convId,
    int csStatus,
    long agentId,
    long visitorUserId,
    String visitorName,
    boolean visitorOnline,
    long visitorReadSeq,
    long lastMsgTimeMs,
    String lastMsgAbstract,
    long maxSeq
) {}
