package com.im.file.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class FilePropertiesTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(PropertiesBindingConfig.class);

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

  @Test
  @DisplayName("Spring can bind FileProperties record via constructor")
  void bindsFromSpringConfigurationProperties() {
    contextRunner
        .withPropertyValues(
            "im.file.endpoint=http://minio:9000",
            "im.file.public-endpoint=http://cdn.example.com",
            "im.file.access-key=ak",
            "im.file.secret-key=sk",
            "im.file.bucket=im-media",
            "im.file.presign-ttl=3m",
            "im.file.download-ttl=10m",
            "im.file.cdn-base-url=https://cdn.example.com",
            "im.file.size-limit.video-bytes=1048576",
            "im.file.transcode.enabled=true",
            "im.file.transcode.ffmpeg-path=/usr/bin/ffmpeg",
            "im.file.transcode.timeout=30s",
            "im.file.transcode.batch-size=2")
        .run(context -> {
          assertThat(context).hasNotFailed();
          FileProperties properties = context.getBean(FileProperties.class);
          assertThat(properties.endpoint()).isEqualTo("http://minio:9000");
          assertThat(properties.publicEndpoint()).isEqualTo("http://cdn.example.com");
          assertThat(properties.presignTtl()).isEqualTo(Duration.ofMinutes(3));
          assertThat(properties.downloadTtl()).isEqualTo(Duration.ofMinutes(10));
          assertThat(properties.cdnBaseUrl()).isEqualTo("https://cdn.example.com");
          assertThat(properties.sizeLimit().videoBytes()).isEqualTo(1_048_576L);
          assertThat(properties.transcode().enabled()).isTrue();
          assertThat(properties.transcode().ffmpegPath()).isEqualTo("/usr/bin/ffmpeg");
          assertThat(properties.transcode().timeout()).isEqualTo(Duration.ofSeconds(30));
          assertThat(properties.transcode().batchSize()).isEqualTo(2);
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(FileProperties.class)
  static class PropertiesBindingConfig {
  }
}
