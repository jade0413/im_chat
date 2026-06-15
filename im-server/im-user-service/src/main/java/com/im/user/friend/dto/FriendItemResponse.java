package com.im.user.friend.dto;

/** 好友列表条目。remark 为本人给对方的备注名。 */
public record FriendItemResponse(
    long userId,
    String remark,
    String nickname,
    String avatar,
    String username) {
}
