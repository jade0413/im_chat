package com.im.message.dto;

public record MessageItemResponse(
    long convId,
    long seq,
    long serverMsgId,
    String clientMsgId,
    long senderId,
    long sendTime,
    int msgType,
    String text) {
}
