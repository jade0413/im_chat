package com.im.common.error;

import java.io.Serial;
import java.util.Objects;

public class ImException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  private final ErrorCode errorCode;

  public ImException(ErrorCode errorCode) {
    this(errorCode, errorCode.defaultMessage(), null);
  }

  public ImException(ErrorCode errorCode, String message) {
    this(errorCode, message, null);
  }

  public ImException(ErrorCode errorCode, Throwable cause) {
    this(errorCode, errorCode.defaultMessage(), cause);
  }

  public ImException(ErrorCode errorCode, String message, Throwable cause) {
    super(message == null || message.isBlank() ? errorCode.defaultMessage() : message, cause);
    this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
  }

  public ErrorCode errorCode() {
    return errorCode;
  }
}
