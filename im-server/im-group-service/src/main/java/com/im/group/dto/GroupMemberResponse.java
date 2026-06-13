package com.im.group.dto;

import java.time.LocalDateTime;

public record GroupMemberResponse(
    long userId,
    int role,
    LocalDateTime joinedAt) {
}
