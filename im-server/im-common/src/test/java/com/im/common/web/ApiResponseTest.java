package com.im.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.im.common.error.ErrorCode;
import com.im.common.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

  @AfterEach
  void tearDown() {
    TraceContext.clear();
  }

  @Test
  void createsSuccessResponseWithCurrentTraceId() {
    TraceContext.setTraceId("trace-ok");

    ApiResponse<String> response = ApiResponse.ok("data");

    assertThat(response.code()).isEqualTo(ErrorCode.OK.code());
    assertThat(response.message()).isEqualTo("ok");
    assertThat(response.data()).isEqualTo("data");
    assertThat(response.traceId()).isEqualTo("trace-ok");
    assertThat(response.timestamp()).isPositive();
  }

  @Test
  void createsFailureResponseWithDefaultMessage() {
    TraceContext.setTraceId("trace-fail");

    ApiResponse<Void> response = ApiResponse.fail(ErrorCode.TOKEN_INVALID);

    assertThat(response.code()).isEqualTo(ErrorCode.TOKEN_INVALID.code());
    assertThat(response.message()).isEqualTo(ErrorCode.TOKEN_INVALID.defaultMessage());
    assertThat(response.data()).isNull();
    assertThat(response.traceId()).isEqualTo("trace-fail");
  }
}
