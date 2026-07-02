package com.im.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 修改个人资料：昵称（必填）+ 头像（可选，null 表示不改）。 */
public record UpdateProfileRequest(
    @NotBlank @Size(max = 32) String nickname,
    String avatar) {
}
