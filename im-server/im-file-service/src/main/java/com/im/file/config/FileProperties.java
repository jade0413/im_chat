package com.im.file.config;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "im.file")
public record FileProperties(
    String endpoint,
    String publicEndpoint,
    String accessKey,
    String secretKey,
    String bucket,
    Duration presignTtl,
    Duration downloadTtl,
    String cdnBaseUrl,
    Set<String> allowedMimes,
    SizeLimit sizeLimit,
    Transcode transcode
) {

  private static final Set<String> DEFAULT_ALLOWED_MIMES = Set.of(
      "image/jpeg",
      "image/png",
      "image/webp",
      "image/gif",
      "image/heic",
      "image/heif",
      "audio/aac",
      "audio/mpeg",
      "audio/ogg",
      "audio/opus",
      "audio/wav",
      "audio/mp4",
      "video/mp4",
      "video/x-m4v",
      "video/webm",
      "video/quicktime",
      "video/x-matroska",
      "video/x-msvideo",
      "video/3gpp",
      "video/3gpp2",
      "video/mpeg",
      "application/pdf",
      "application/zip",
      "application/vnd.rar",
      "application/x-7z-compressed",
      "application/msword",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "application/vnd.ms-excel",
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "application/vnd.ms-powerpoint",
      "application/vnd.openxmlformats-officedocument.presentationml.presentation",
      "application/octet-stream",
      "text/csv",
      "text/plain");

  public FileProperties {
    endpoint = firstText(endpoint, env("IM_MINIO_ENDPOINT"), env("MINIO_ENDPOINT"), "http://localhost:9000");
    publicEndpoint = firstText(publicEndpoint, env("IM_MINIO_PUBLIC_ENDPOINT"), env("MINIO_PUBLIC_ENDPOINT"), endpoint);
    accessKey = firstText(accessKey, env("IM_MINIO_ACCESS_KEY"), env("MINIO_ROOT_USER"), "im_minio");
    secretKey = firstText(secretKey, env("IM_MINIO_SECRET_KEY"), env("MINIO_ROOT_PASSWORD"), "im_dev_minio_pwd");
    bucket = firstText(bucket, env("IM_MINIO_BUCKET"), env("MINIO_BUCKET"), "im-media");
    if (presignTtl == null) {
      presignTtl = Duration.ofMinutes(5);
    }
    if (downloadTtl == null) {
      downloadTtl = Duration.ofMinutes(15);
    }
    cdnBaseUrl = firstText(cdnBaseUrl, env("IM_FILE_CDN_BASE_URL"), env("CDN_BASE_URL"), "");
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
    if (transcode == null) {
      transcode = new Transcode(null, null, null, null);
    }
  }

  public FileProperties(String endpoint,
      String publicEndpoint,
      String accessKey,
      String secretKey,
      String bucket,
      Duration presignTtl,
      Set<String> allowedMimes,
      SizeLimit sizeLimit) {
    this(endpoint, publicEndpoint, accessKey, secretKey, bucket, presignTtl, null, null,
        allowedMimes, sizeLimit, null);
  }

  public FileProperties(String endpoint,
      String publicEndpoint,
      String accessKey,
      String secretKey,
      String bucket,
      Duration presignTtl,
      Duration downloadTtl,
      String cdnBaseUrl,
      Set<String> allowedMimes,
      SizeLimit sizeLimit) {
    this(endpoint, publicEndpoint, accessKey, secretKey, bucket, presignTtl, downloadTtl,
        cdnBaseUrl, allowedMimes, sizeLimit, null);
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

  public record Transcode(Boolean enabled,
      String ffmpegPath,
      String workDir,
      Duration timeout,
      Duration pollInterval,
      Integer batchSize) {

    public Transcode {
      enabled = enabled != null && enabled;
      ffmpegPath = firstText(ffmpegPath, env("IM_FILE_FFMPEG_PATH"), env("FFMPEG_PATH"), "ffmpeg");
      workDir = firstText(workDir, env("IM_FILE_TRANSCODE_WORK_DIR"),
          System.getProperty("java.io.tmpdir") + "/im-file-transcode");
      if (timeout == null) {
        timeout = Duration.ofMinutes(10);
      }
      if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()) {
        pollInterval = Duration.ofSeconds(10);
      }
      if (batchSize == null || batchSize <= 0) {
        batchSize = 4;
      }
    }

    public Transcode(Boolean enabled, String ffmpegPath, String workDir, Duration timeout) {
      this(enabled, ffmpegPath, workDir, timeout, null, null);
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
