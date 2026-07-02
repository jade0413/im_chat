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

  public static String moderationEventDedup(long tenantId, long eventId) {
    validatePositive("tenantId", tenantId);
    validatePositive("eventId", eventId);
    return "moderation:event:" + tenantId + ":" + eventId;
  }

  public static String consumerDedup(String namespace, long tenantId, String key) {
    validateNamespace(namespace);
    validatePositive("tenantId", tenantId);
    validateText("key", key);
    return "consumer:dedup:" + namespace + ":" + tenantId + ":" + key;
  }

  public static String messageDedup(long tenantId, String clientMsgId) {
    validatePositive("tenantId", tenantId);
    if (clientMsgId == null || clientMsgId.isBlank()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "client_msg_id is required");
    }
    return "dedup:" + tenantId + ":" + clientMsgId;
  }

  public static String convMembers(long tenantId, long convId) {
    validatePositive("tenantId", tenantId);
    validatePositive("convId", convId);
    return "conv:members:" + tenantId + ":" + convId;
  }

  /** 免鉴权 widget 访客接入端点的固定窗口频控键（按租户 + 来源 IP）。 */
  public static String widgetSessionRate(long tenantId, String clientIp) {
    validatePositive("tenantId", tenantId);
    validateText("clientIp", clientIp);
    return "ratelimit:widget:" + tenantId + ":" + clientIp;
  }

  public static String workerIdLease(long workerId) {
    if (workerId < 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "workerId must be non-negative");
    }
    return "im:worker:" + workerId;
  }

  // ---- 实时通话（D45）----

  /** 呼叫会话 HASH：caller_id/callee_id/media/state/client_call_id/invite_at_ms/answer_at_ms。 */
  public static String callSession(long tenantId, String callId) {
    validatePositive("tenantId", tenantId);
    validateText("callId", callId);
    return "call:" + tenantId + ":" + callId;
  }

  /** 用户占线标记 STRING=callId：存在即忙线（振铃/通话中）。 */
  public static String callUserBusy(long tenantId, long userId) {
    validatePositive("tenantId", tenantId);
    validatePositive("userId", userId);
    return "call:busy:" + tenantId + ":" + userId;
  }

  /** INVITE 幂等 STRING clientCallId -> callId。 */
  public static String callIdempotency(long tenantId, String clientCallId) {
    validatePositive("tenantId", tenantId);
    validateText("clientCallId", clientCallId);
    return "call:idem:" + tenantId + ":" + clientCallId;
  }

  /** 振铃超时 ZSET（全局单键，member = tenantId:callId，score = 截止时间戳 ms）。 */
  public static String callRingDeadlines() {
    return "call:deadline";
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

  private static void validateNamespace(String namespace) {
    validateText("namespace", namespace);
    if (!namespace.matches("[a-zA-Z0-9_.-]{1,64}")) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "namespace is invalid");
    }
  }
}
