package com.im.common.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.im.common.error.ImException;
import com.im.proto.common.Platform;
import org.junit.jupiter.api.Test;

class PlatformClassTest {

  @Test
  void mapsConcretePlatformsToPolicyClasses() {
    assertThat(PlatformClass.fromPlatform(Platform.IOS_VALUE)).isEqualTo(PlatformClass.MOBILE);
    assertThat(PlatformClass.fromPlatform(Platform.ANDROID_VALUE)).isEqualTo(PlatformClass.MOBILE);
    assertThat(PlatformClass.fromPlatform(Platform.WINDOWS_VALUE)).isEqualTo(PlatformClass.DESKTOP);
    assertThat(PlatformClass.fromPlatform(Platform.MACOS_VALUE)).isEqualTo(PlatformClass.DESKTOP);
    assertThat(PlatformClass.fromPlatform(Platform.WEB_VALUE)).isEqualTo(PlatformClass.WEB);
    assertThat(PlatformClass.fromPlatform(Platform.MINI_PROGRAM_VALUE)).isEqualTo(PlatformClass.WEB);
  }

  @Test
  void rejectsUnknownPlatform() {
    assertThatThrownBy(() -> PlatformClass.fromPlatform(999))
        .isInstanceOf(ImException.class);
  }
}
