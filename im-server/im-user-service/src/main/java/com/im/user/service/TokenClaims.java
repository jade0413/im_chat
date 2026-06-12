package com.im.user.service;

import java.time.Instant;

public record TokenClaims(
    long tenantId,
    long userId,
    String tokenType,
    Instant issuedAt,
    Instant expiresAt,
    String platformClass,
    long tokenVersion
) {
}
