package com.im.bootstrap.e2e;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfSystemProperty(named = "im.e2e.external", matches = "true")
class ImServerMvpExternalSmokeTest extends AbstractImServerMvpSmokeTest {

  private static final String DATABASE = "im_smoke_" + Long.toUnsignedString(System.currentTimeMillis(), 36);
  private static final String MYSQL_ADMIN_URL = property("im.e2e.mysql.admin-url", "IM_E2E_MYSQL_ADMIN_URL",
      "jdbc:mysql://127.0.0.1:3306/?useUnicode=true&characterEncoding=utf8&useSSL=false"
          + "&allowPublicKeyRetrieval=true&serverTimezone=UTC");
  private static final String MYSQL_USERNAME = property("im.e2e.mysql.username", "IM_E2E_MYSQL_USERNAME", "root");
  private static final String MYSQL_PASSWORD = property("im.e2e.mysql.password", "IM_E2E_MYSQL_PASSWORD", "");
  private static final String MYSQL_URL = property("im.e2e.mysql.url", "IM_E2E_MYSQL_URL",
      "jdbc:mysql://127.0.0.1:3306/" + DATABASE + "?useUnicode=true&characterEncoding=utf8&useSSL=false"
          + "&allowPublicKeyRetrieval=true&serverTimezone=UTC");

  @BeforeAll
  static void initializeExternalSchema() throws Exception {
    try (Connection connection = DriverManager.getConnection(MYSQL_ADMIN_URL, MYSQL_USERNAME, MYSQL_PASSWORD)) {
      connection.createStatement().executeUpdate("CREATE DATABASE IF NOT EXISTS `" + DATABASE + "`");
      SchemaScripts.applyInitialSchema(connection, DATABASE);
    }
  }

  @AfterAll
  static void dropExternalSchema() throws SQLException {
    try (Connection connection = DriverManager.getConnection(MYSQL_ADMIN_URL, MYSQL_USERNAME, MYSQL_PASSWORD)) {
      connection.createStatement().executeUpdate("DROP DATABASE IF EXISTS `" + DATABASE + "`");
    }
  }

  @DynamicPropertySource
  static void registerExternalSmokeProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> MYSQL_URL);
    registry.add("spring.datasource.username", () -> MYSQL_USERNAME);
    registry.add("spring.datasource.password", () -> MYSQL_PASSWORD);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.sql.init.mode", () -> "never");
    registry.add("spring.data.redis.host", () -> property("im.e2e.redis.host", "IM_E2E_REDIS_HOST", "127.0.0.1"));
    registry.add("spring.data.redis.port", () -> property("im.e2e.redis.port", "IM_E2E_REDIS_PORT", "6379"));
    registry.add("spring.data.redis.password", () -> property("im.e2e.redis.password", "IM_E2E_REDIS_PASSWORD", ""));
    registry.add("spring.rabbitmq.host", () -> property("im.e2e.rabbitmq.host", "IM_E2E_RABBITMQ_HOST", "localhost"));
    registry.add("spring.rabbitmq.port", () -> property("im.e2e.rabbitmq.port", "IM_E2E_RABBITMQ_PORT", "5672"));
    registry.add("spring.rabbitmq.username", () -> property("im.e2e.rabbitmq.username", "IM_E2E_RABBITMQ_USERNAME", "im"));
    registry.add("spring.rabbitmq.password",
        () -> property("im.e2e.rabbitmq.password", "IM_E2E_RABBITMQ_PASSWORD", "im_dev_mq_pwd"));
    registry.add("im.grpc.port", () -> GRPC_PORT);
    registry.add("im.rpc.conversation.host", () -> "localhost");
    registry.add("im.rpc.conversation.port", () -> GRPC_PORT);
    registry.add("im.outbox.enabled", () -> false);
  }

  private static String property(String propertyName, String envName, String defaultValue) {
    String value = System.getProperty(propertyName);
    if (value != null && !value.isBlank()) {
      return value;
    }
    value = System.getenv(envName);
    if (value != null && !value.isBlank()) {
      return value;
    }
    return defaultValue;
  }

}
