package com.im.message.moderation;

import java.math.BigDecimal;

public record MediaModerationMatch(
    String provider,
    String category,
    BigDecimal score,
    String evidence
) {
}
