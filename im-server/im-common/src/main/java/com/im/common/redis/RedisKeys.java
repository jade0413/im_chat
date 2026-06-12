package com.im.common.redis;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;

public final class RedisKeys {

  private RedisKeys() {
  }

  public static String route(long tenantId, long userId, String platformClass) {
    validatePositive("tenantId", tenantId);
    validatePositive("userId", userId);
    validateText("platformClass", platformClass);
    return "route:" + tenantId + ":" + userId + ":" + platformClass;
  }

  public static String userRoutePattern(long tenantId, long userId) {
    validatePositive("tenantId", tenantId);
    validatePositive("userId", userId);
    return "route:" + tenantId + ":" + userId + ":*";
  }

  public static String tokenVersion(long tenantId, long userId, String platformClass) {
    validatePositive("tenantId", tenantId);
    validatePositive("userId", userId);
    validateText("platformClass", platformClass);
    return "token_ver:" + tenantId + ":" + userId + ":" + platformClass;
  }

  public static String pushEventDedup(long tenantId, long eventId) {
    validatePositive("tenantId", tenantId);
    validatePositive("eventId", eventId);
    return "push:event:" + tenantId + ":" + eventId;
  }

  public static String messageDedup(long tenantId, String clientMsgId) {
    validatePositive("tenantId", tenantId);
    if (clientMsgId == null || clientMsgId.isBlank()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "client_msg_id is required");
    }
    return "dedup:" + tenantId + ":" + clientMsgId;
  }

  public static String workerIdLease(long workerId) {
    if (workerId < 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "workerId must be non-negative");
    }
    return "im:worker:" + workerId;
  }

  private static void validatePositive(String name, long value) {
    if (value <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, name + " must be positive");
    }
  }

  private static void validateText(String name, String value) {
    if (value == null || value.isBlank()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, name + " is required");
    }
  }
}
