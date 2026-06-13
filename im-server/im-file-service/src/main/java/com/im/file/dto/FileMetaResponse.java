package com.im.file.dto;

public record FileMetaResponse(
    long fileId,
    String objectKey,
    String mime,
    long size,
    Integer durationMs,
    int status
) {
}
