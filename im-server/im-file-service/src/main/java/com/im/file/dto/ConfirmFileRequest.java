package com.im.file.dto;

public record ConfirmFileRequest(
    String objectKey,
    Long size,
    String mime
) {
}
