package com.im.file.dto;

public record DownloadFileResponse(
    String objectKey,
    String url,
    long expiresAt,
    boolean transformed
) {
}
