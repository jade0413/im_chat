package com.im.common.auth;

import java.time.Instant;

/**
 * 解码后的 JWT claims。
 * platformClass / tokenVersion 对访客 token 为 null / 0（访客无平台类限制）。
 */
public record AuthTokenClaims(
    long tenantId,
    long userId,
    Instant expiresAt,
    String platformClass,
    long tokenVersion) {

  /** 向后兼容：仅需 tenantId/userId/expiresAt 时使用 */
  public AuthTokenClaims(long tenantId, long userId, Instant expiresAt) {
    this(tenantId, userId, expiresAt, null, 0L);
  }
}
