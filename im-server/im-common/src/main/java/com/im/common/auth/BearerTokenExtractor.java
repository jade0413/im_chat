package com.im.common.auth;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;

public final class BearerTokenExtractor {

  private static final String PREFIX = "Bearer ";

  private BearerTokenExtractor() {
  }

  public static String extract(String authorization) {
    if (authorization == null || authorization.isBlank()) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    if (!authorization.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    String token = authorization.substring(PREFIX.length()).trim();
    if (token.isBlank()) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    return token;
  }
}
