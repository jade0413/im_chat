package com.im.file.dto;

import java.util.Map;

public record PresignFileResponse(
    long fileId,
    String objectKey,
    String uploadUrl,
    long expiresAt,
    Map<String, String> requiredHeaders
) {
}
