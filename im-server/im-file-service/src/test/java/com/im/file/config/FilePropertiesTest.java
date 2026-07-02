package com.im.file.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class FilePropertiesTest {

  @Test
  void defaultAllowedMimesMatchClientAttachmentTypes() {
    FileProperties properties = new FileProperties(
        "http://minio:9000",
        "http://localhost:9000",
        "ak",
        "sk",
        "im-media",
        Duration.ofMinutes(5),
        null,
        null);

    assertThat(properties.isMimeAllowed("image/heic")).isTrue();
    assertThat(properties.isMimeAllowed("image/heif")).isTrue();
    assertThat(properties.isMimeAllowed("application/msword")).isTrue();
    assertThat(properties.isMimeAllowed(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document")).isTrue();
    assertThat(properties.isMimeAllowed("application/vnd.ms-excel")).isTrue();
    assertThat(properties.isMimeAllowed(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).isTrue();
  }
}
