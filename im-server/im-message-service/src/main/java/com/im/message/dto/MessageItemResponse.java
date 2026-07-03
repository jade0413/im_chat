package com.im.message.dto;

public record MessageItemResponse(
    long convId,
    long seq,
    long serverMsgId,
    String clientMsgId,
    long senderId,
    long sendTime,
    int msgType,
    int status,
    int revokeReason,
    String text,
    String objectKey,
    String thumbKey,
    String fileName,
    String mime,
    Long size,
    Integer durationMs,
    Integer width,
    Integer height,
    String codec) {
}
