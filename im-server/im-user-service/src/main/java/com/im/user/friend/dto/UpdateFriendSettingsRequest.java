package com.im.user.friend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 好友设置（D40）：friendVerifyRequired 1=加我需验证(默认) 0=免验证。 */
public record UpdateFriendSettingsRequest(
    @NotNull @Min(0) @Max(1) Integer friendVerifyRequired) {
}
