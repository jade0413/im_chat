package com.im.common.web;

import com.im.common.error.ErrorCode;
import com.im.common.trace.TraceContext;
import java.time.Instant;

public record ApiResponse<T>(
    int code,
    String message,
    T data,
    String traceId,
    long timestamp
) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(
        ErrorCode.OK.code(),
        ErrorCode.OK.defaultMessage(),
        data,
        TraceContext.currentOrCreateTraceId(),
        Instant.now().toEpochMilli()
    );
  }

  public static ApiResponse<Void> fail(ErrorCode errorCode) {
    return fail(errorCode, errorCode.defaultMessage());
  }

  public static ApiResponse<Void> fail(ErrorCode errorCode, String message) {
    return new ApiResponse<>(
        errorCode.code(),
        message == null || message.isBlank() ? errorCode.defaultMessage() : message,
        null,
        TraceContext.currentOrCreateTraceId(),
        Instant.now().toEpochMilli()
    );
  }
}
