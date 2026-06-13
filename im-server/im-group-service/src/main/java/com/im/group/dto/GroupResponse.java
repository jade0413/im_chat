package com.im.group.dto;

public record GroupResponse(
    long groupId,
    long convId,
    String name,
    long ownerId,
    int memberCount
) {
}
