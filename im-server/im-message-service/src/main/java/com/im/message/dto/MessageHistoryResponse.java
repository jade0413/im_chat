package com.im.message.dto;

import java.util.List;

public record MessageHistoryResponse(
    long convId,
    List<MessageItemResponse> messages,
    boolean hasMore) {
}
