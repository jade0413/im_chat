package com.im.common.auth;

import java.time.Instant;

public record AuthTokenClaims(long tenantId, long userId, Instant expiresAt) {
}
