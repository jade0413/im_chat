package com.im.user.dto;

import java.time.LocalDateTime;

public record UserProfileResponse(
    long id,
    long tenantId,
    String account,
    String nickname,
    String avatar,
    int userType,
    int verifiedType,
    int status,
    LocalDateTime createdAt,
    String username,
    int friendVerifyRequired,
    boolean isAgent,
    int agentStatus
) {
}
