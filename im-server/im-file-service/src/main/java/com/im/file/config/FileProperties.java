package com.im.file.config;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "im.file")
public record FileProperties(
    String endpoint,
    String accessKey,
    String secretKey,
    String bucket,
    Duration presignTtl,
    Set<String> allowedMimes,
    SizeLimit sizeLimit
) {

  private static final Set<String> DEFAULT_ALLOWED_MIMES = Set.of(
      "image/jpeg",
      "image/png",
      "image/webp",
      "image/gif",
      "audio/aac",
      "audio/mpeg",
      "audio/ogg",
      "audio/opus",
      "audio/wav",
      "audio/mp4",
      "video/mp4",
      "video/webm",
      "video/quicktime",
      "application/pdf",
      "application/zip",
      "application/octet-stream",
      "text/plain");

  public FileProperties {
    endpoint = firstText(endpoint, env("IM_MINIO_ENDPOINT"), env("MINIO_ENDPOINT"), "http://localhost:9000");
    accessKey = firstText(accessKey, env("IM_MINIO_ACCESS_KEY"), env("MINIO_ROOT_USER"), "im_minio");
    secretKey = firstText(secretKey, env("IM_MINIO_SECRET_KEY"), env("MINIO_ROOT_PASSWORD"), "im_dev_minio_pwd");
    bucket = firstText(bucket, env("IM_MINIO_BUCKET"), env("MINIO_BUCKET"), "im-media");
    if (presignTtl == null) {
      presignTtl = Duration.ofMinutes(5);
    }
    if (allowedMimes == null || allowedMimes.isEmpty()) {
      allowedMimes = DEFAULT_ALLOWED_MIMES;
    } else {
      allowedMimes = allowedMimes.stream()
          .filter(FileProperties::hasText)
          .map(FileProperties::normalizeMime)
          .collect(Collectors.toUnmodifiableSet());
    }
    if (sizeLimit == null) {
      sizeLimit = new SizeLimit(null, null, null, null);
    }
  }

  public long maxBytesFor(String mime) {
    String normalized = normalizeMime(mime);
    if (normalized.startsWith("image/")) {
      return sizeLimit.imageBytes();
    }
    if (normalized.startsWith("audio/")) {
      return sizeLimit.voiceBytes();
    }
    if (normalized.startsWith("video/")) {
      return sizeLimit.videoBytes();
    }
    return sizeLimit.fileBytes();
  }

  public boolean isMimeAllowed(String mime) {
    return allowedMimes.contains(normalizeMime(mime));
  }

  public record SizeLimit(Long imageBytes, Long voiceBytes, Long videoBytes, Long fileBytes) {

    public SizeLimit {
      if (imageBytes == null) {
        imageBytes = 10L * 1024 * 1024;
      }
      if (voiceBytes == null) {
        voiceBytes = 20L * 1024 * 1024;
      }
      if (videoBytes == null) {
        videoBytes = 200L * 1024 * 1024;
      }
      if (fileBytes == null) {
        fileBytes = 50L * 1024 * 1024;
      }
    }
  }

  public static String normalizeMime(String mime) {
    return mime == null ? "" : mime.trim().toLowerCase(Locale.ROOT);
  }

  private static String firstText(String... values) {
    for (String value : values) {
      if (hasText(value)) {
        return value.trim();
      }
    }
    return "";
  }

  private static String env(String name) {
    return System.getenv(name);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
