package com.im.group.dto;

import java.util.List;

public record GroupMemberChangeResponse(
    long groupId,
    long convId,
    int memberCount,
    List<Long> changedUserIds
) {
}
