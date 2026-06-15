package com.im.user.friend.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 发起好友申请（D40）。note 备注对方可见。 */
public record SendFriendRequestRequest(
    @Positive long toUserId,
    @Size(max = 128) String note) {
}
