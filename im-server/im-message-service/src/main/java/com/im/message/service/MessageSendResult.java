package com.im.message.service;

public record MessageSendResult(
    long serverMsgId,
    long conversationId,
    long seq,
    long serverTimeMillis) {
}
