package com.im.common.error;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

public enum ErrorCode {
  OK(com.im.proto.common.ErrorCode.OK.getNumber(), "ok", HttpStatus.OK),

  TOKEN_INVALID(com.im.proto.common.ErrorCode.TOKEN_INVALID.getNumber(), "token invalid", HttpStatus.UNAUTHORIZED),
  TOKEN_EXPIRED(com.im.proto.common.ErrorCode.TOKEN_EXPIRED.getNumber(), "token expired", HttpStatus.UNAUTHORIZED),
  TENANT_DISABLED(com.im.proto.common.ErrorCode.TENANT_DISABLED.getNumber(), "tenant disabled", HttpStatus.FORBIDDEN),
  USER_BANNED(com.im.proto.common.ErrorCode.USER_BANNED.getNumber(), "user banned", HttpStatus.FORBIDDEN),
  REPLAY_REJECTED(com.im.proto.common.ErrorCode.REPLAY_REJECTED.getNumber(), "request replay rejected", HttpStatus.UNAUTHORIZED),

  BLOCKED_BY_PEER(com.im.proto.common.ErrorCode.BLOCKED_BY_PEER.getNumber(), "blocked by peer", HttpStatus.FORBIDDEN),
  FRIEND_REQUIRED(com.im.proto.common.ErrorCode.FRIEND_REQUIRED.getNumber(), "friend relation required", HttpStatus.FORBIDDEN),
  MUTED(com.im.proto.common.ErrorCode.MUTED.getNumber(), "user muted", HttpStatus.FORBIDDEN),
  MSG_TOO_LARGE(com.im.proto.common.ErrorCode.MSG_TOO_LARGE.getNumber(), "message too large", HttpStatus.BAD_REQUEST),
  REVOKE_WINDOW_EXPIRED(com.im.proto.common.ErrorCode.REVOKE_WINDOW_EXPIRED.getNumber(), "revoke window expired", HttpStatus.BAD_REQUEST),

  CONV_NOT_FOUND(com.im.proto.common.ErrorCode.CONV_NOT_FOUND.getNumber(), "conversation not found", HttpStatus.NOT_FOUND),
  NOT_CONV_MEMBER(com.im.proto.common.ErrorCode.NOT_CONV_MEMBER.getNumber(), "not conversation member", HttpStatus.FORBIDDEN),

  GROUP_NOT_FOUND(com.im.proto.common.ErrorCode.GROUP_NOT_FOUND.getNumber(), "group not found", HttpStatus.NOT_FOUND),
  GROUP_FULL(com.im.proto.common.ErrorCode.GROUP_FULL.getNumber(), "group is full", HttpStatus.BAD_REQUEST),
  NOT_GROUP_MEMBER(com.im.proto.common.ErrorCode.NOT_GROUP_MEMBER.getNumber(), "not group member", HttpStatus.FORBIDDEN),
  NO_PERMISSION(com.im.proto.common.ErrorCode.NO_PERMISSION.getNumber(), "no permission", HttpStatus.FORBIDDEN),

  FILE_TOO_LARGE(com.im.proto.common.ErrorCode.FILE_TOO_LARGE.getNumber(), "file too large", HttpStatus.BAD_REQUEST),
  QUOTA_EXCEEDED(com.im.proto.common.ErrorCode.QUOTA_EXCEEDED.getNumber(), "quota exceeded", HttpStatus.FORBIDDEN),
  MIME_NOT_ALLOWED(com.im.proto.common.ErrorCode.MIME_NOT_ALLOWED.getNumber(), "mime not allowed", HttpStatus.BAD_REQUEST),

  RATE_LIMITED(com.im.proto.common.ErrorCode.RATE_LIMITED.getNumber(), "rate limited", HttpStatus.TOO_MANY_REQUESTS),
  TENANT_QUOTA_EXCEEDED(com.im.proto.common.ErrorCode.TENANT_QUOTA_EXCEEDED.getNumber(), "tenant quota exceeded", HttpStatus.FORBIDDEN),
  CONTENT_REJECTED(com.im.proto.common.ErrorCode.CONTENT_REJECTED.getNumber(), "content rejected", HttpStatus.BAD_REQUEST),

  VALIDATION_FAILED(9001, "validation failed", HttpStatus.BAD_REQUEST),
  INTERNAL_ERROR(9999, "internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

  private static final Map<Integer, ErrorCode> BY_CODE = Arrays.stream(values())
      .collect(Collectors.toUnmodifiableMap(ErrorCode::code, Function.identity()));

  private final int code;
  private final String defaultMessage;
  private final HttpStatus httpStatus;

  ErrorCode(int code, String defaultMessage, HttpStatus httpStatus) {
    this.code = code;
    this.defaultMessage = defaultMessage;
    this.httpStatus = httpStatus;
  }

  public int code() {
    return code;
  }

  public String defaultMessage() {
    return defaultMessage;
  }

  public HttpStatus httpStatus() {
    return httpStatus;
  }

  public boolean success() {
    return this == OK;
  }

  public static Optional<ErrorCode> fromCode(int code) {
    return Optional.ofNullable(BY_CODE.get(code));
  }
}
