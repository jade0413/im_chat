package com.im.bootstrap.selfcheck;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "im.startup-check")
public record StartupSelfCheckProperties(
    boolean enabled,
    boolean mysql,
    boolean redis,
    boolean rabbitmq,
    boolean minio,
    List<String> requiredTables,
    Minio minioConfig
) {

  private static final List<String> DEFAULT_REQUIRED_TABLES = List.of(
      "tenant",
      "user",
      "conversation",
      "conversation_member",
      "message",
      "outbox_event");

  public StartupSelfCheckProperties {
    requiredTables = requiredTables == null || requiredTables.isEmpty()
        ? DEFAULT_REQUIRED_TABLES
        : List.copyOf(requiredTables);
    minioConfig = minioConfig == null ? new Minio(null, null, null, "im-media") : minioConfig;
  }

  public record Minio(
      String endpoint,
      String accessKey,
      String secretKey,
      String bucket
  ) {
  }
}
