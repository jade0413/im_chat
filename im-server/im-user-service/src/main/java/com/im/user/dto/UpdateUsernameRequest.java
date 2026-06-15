package com.im.user.dto;

import jakarta.validation.constraints.NotBlank;

/** 设置/修改唯一用户名（D42）。 */
public record UpdateUsernameRequest(
    @NotBlank String username) {
}
