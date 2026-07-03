package com.im.message.moderation;

public record MediaModerationRequest(
    long tenantId,
    long messageId,
    String objectKey,
    String mime,
    long size,
    String mediaType
) {
}
