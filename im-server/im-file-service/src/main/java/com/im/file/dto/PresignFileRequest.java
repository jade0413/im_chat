package com.im.file.dto;

public record PresignFileRequest(
    String fileName,
    String mime,
    long size,
    Integer durationMs
) {
}
