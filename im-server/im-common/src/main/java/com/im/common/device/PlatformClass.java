package com.im.common.device;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.proto.common.Platform;

/**
 * Platform class is a cross-cutting connection concept used by auth and push routing.
 * It lives in im-common so user-service and push-service share the same D11 mapping
 * without depending on each other's business modules.
 */
public enum PlatformClass {
  MOBILE("mobile"),
  DESKTOP("desktop"),
  WEB("web");

  private final String key;

  PlatformClass(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }

  public static PlatformClass fromPlatform(int platform) {
    return switch (platform) {
      case Platform.IOS_VALUE, Platform.ANDROID_VALUE -> MOBILE;
      case Platform.WINDOWS_VALUE, Platform.MACOS_VALUE -> DESKTOP;
      case Platform.WEB_VALUE, Platform.MINI_PROGRAM_VALUE -> WEB;
      default -> throw new ImException(ErrorCode.VALIDATION_FAILED, "unsupported platform");
    };
  }

  public static PlatformClass fromKey(String key) {
    for (PlatformClass value : values()) {
      if (value.key.equals(key)) {
        return value;
      }
    }
    throw new ImException(ErrorCode.TOKEN_INVALID, "invalid platform class");
  }

  public static PlatformClass defaultForRest() {
    return WEB;
  }
}
