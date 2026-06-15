package com.im.user.friend.dto;

/**
 * 申请历史条目（供联系人"通知"页渲染）。status 取自 friend_request（状态唯一真相），
 * 客户端按 status 决定按钮：0 待处理 / 1 已同意 / 2 已拒绝 / 3 已忽略。
 * peer* 为对端展示资料（incoming 时是申请人，outgoing 时是被申请人）。
 */
public record FriendRequestItemResponse(
    long requestId,
    long fromUserId,
    long toUserId,
    String note,
    int status,
    boolean autoAccepted,
    long createTime,
    long peerUserId,
    String peerNickname,
    String peerAvatar,
    String peerUsername) {
}
