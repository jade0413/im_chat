package com.im.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorCodeTest {

  @Test
  void mapsProtoErrorCodeNumbers() {
    assertThat(ErrorCode.TOKEN_INVALID.code())
        .isEqualTo(com.im.proto.common.ErrorCode.TOKEN_INVALID.getNumber());
    assertThat(ErrorCode.MSG_TOO_LARGE.code())
        .isEqualTo(com.im.proto.common.ErrorCode.MSG_TOO_LARGE.getNumber());
    assertThat(ErrorCode.RATE_LIMITED.code())
        .isEqualTo(com.im.proto.common.ErrorCode.RATE_LIMITED.getNumber());
  }

  @Test
  void findsErrorCodeByNumericCode() {
    assertThat(ErrorCode.fromCode(com.im.proto.common.ErrorCode.TOKEN_EXPIRED.getNumber()))
        .contains(ErrorCode.TOKEN_EXPIRED);
    assertThat(ErrorCode.fromCode(-1)).isEmpty();
  }

  @Test
  void imExceptionUsesDefaultMessageWhenMessageIsBlank() {
    ImException exception = new ImException(ErrorCode.TOKEN_INVALID, " ");

    assertThat(exception.errorCode()).isEqualTo(ErrorCode.TOKEN_INVALID);
    assertThat(exception.getMessage()).isEqualTo(ErrorCode.TOKEN_INVALID.defaultMessage());
  }
}
