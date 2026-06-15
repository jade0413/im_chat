package com.im.user.friend.dto;

import jakarta.validation.constraints.Size;

/** 修改好友备注名。 */
public record UpdateRemarkRequest(
    @Size(max = 64) String remark) {
}
