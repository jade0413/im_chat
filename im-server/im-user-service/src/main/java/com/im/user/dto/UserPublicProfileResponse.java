package com.im.user.dto;

/** 其他用户的公开资料，不含账号/手机号等敏感字段；username 是可分享的对外标识（D42）。 */
public record UserPublicProfileResponse(
    long id,
    String nickname,
    String avatar,
    int userType,
    int verifiedType,
    String username) {
}
