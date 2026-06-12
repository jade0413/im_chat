package com.im.conversation.service;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import org.springframework.stereotype.Component;

@Component
public class C2cKeyGenerator {

  public String generate(long firstUserId, long secondUserId) {
    if (firstUserId <= 0 || secondUserId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "user id must be positive");
    }
    if (firstUserId == secondUserId) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "c2c peer must be different user");
    }
    long smaller = Math.min(firstUserId, secondUserId);
    long larger = Math.max(firstUserId, secondUserId);
    return smaller + "_" + larger;
  }
}
