package com.im.common.mq;

public record RabbitMqEvent(
    long eventId,
    long tenantId,
    String eventType,
    String routingKey,
    byte[] payload) {
}
