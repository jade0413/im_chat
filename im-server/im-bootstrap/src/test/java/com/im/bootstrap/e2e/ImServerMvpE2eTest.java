package com.im.bootstrap.e2e;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class ImServerMvpE2eTest extends AbstractImServerMvpSmokeTest {

  @Container
  static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
      DockerImageName.parse("mysql:8.4.0"))
      .withDatabaseName("im")
      .withUsername("im_test")
      .withPassword("im_test_pwd");

  @Container
  static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
      .withExposedPorts(6379);

  @DynamicPropertySource
  static void registerE2eProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    registry.add("spring.sql.init.mode", () -> "never");
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("im.grpc.port", () -> GRPC_PORT);
    registry.add("im.rpc.conversation.host", () -> "localhost");
    registry.add("im.rpc.conversation.port", () -> GRPC_PORT);
    registry.add("im.outbox.enabled", () -> false);
  }

  @BeforeAll
  static void initializeSchema() throws SQLException {
    try (Connection connection = DriverManager.getConnection(
        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
      SchemaScripts.applyInitialSchema(connection);
    }
  }
}
