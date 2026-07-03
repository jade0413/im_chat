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
    assertThat(properties.isMimeAllowed("video/x-m4v")).isTrue();
    assertThat(properties.isMimeAllowed("video/x-matroska")).isTrue();
    assertThat(properties.isMimeAllowed("video/x-msvideo")).isTrue();
    assertThat(properties.isMimeAllowed("video/3gpp")).isTrue();
    assertThat(properties.isMimeAllowed("video/3gpp2")).isTrue();
    assertThat(properties.isMimeAllowed("video/mpeg")).isTrue();
    assertThat(properties.isMimeAllowed("application/msword")).isTrue();
    assertThat(properties.isMimeAllowed(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document")).isTrue();
    assertThat(properties.isMimeAllowed("application/vnd.ms-excel")).isTrue();
    assertThat(properties.isMimeAllowed(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).isTrue();
    assertThat(properties.isMimeAllowed("application/vnd.ms-powerpoint")).isTrue();
    assertThat(properties.isMimeAllowed(
        "application/vnd.openxmlformats-officedocument.presentationml.presentation")).isTrue();
    assertThat(properties.isMimeAllowed("text/csv")).isTrue();
    assertThat(properties.isMimeAllowed("application/vnd.rar")).isTrue();
    assertThat(properties.isMimeAllowed("application/x-7z-compressed")).isTrue();
    assertThat(properties.maxBytesFor("video/x-m4v")).isEqualTo(200L * 1024 * 1024);
    assertThat(properties.downloadTtl()).isEqualTo(Duration.ofMinutes(15));
    assertThat(properties.cdnBaseUrl()).isBlank();
  }
}
