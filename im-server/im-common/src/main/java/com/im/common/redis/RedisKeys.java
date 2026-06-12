package com.im.common.redis;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;

public final class RedisKeys {

  private RedisKeys() {
  }

  public static String conversationSeq(long tenantId, long conversationId) {
    validatePositive("tenantId", tenantId);
    validatePositive("conversationId", conversationId);
    return "seq:" + tenantId + ":" + conversationId;
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
}
