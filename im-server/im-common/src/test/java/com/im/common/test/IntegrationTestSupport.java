package com.im.common.test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTestSupport {

  private static final AtomicBoolean SCHEMA_INITIALIZED = new AtomicBoolean(false);

  @Container
  protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
      DockerImageName.parse("mysql:8.4.0"))
      .withDatabaseName("im")
      .withUsername("im_test")
      .withPassword("im_test_pwd");

  @DynamicPropertySource
  static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    registry.add("spring.sql.init.mode", () -> "never");
  }

  @BeforeAll
  static void initializeSchema() throws SQLException {
    if (!SCHEMA_INITIALIZED.compareAndSet(false, true)) {
      return;
    }
    try (Connection connection = DriverManager.getConnection(
        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
      ScriptUtils.executeSqlScript(
          connection,
          new EncodedResource(new FileSystemResource(findSchemaPath()), StandardCharsets.UTF_8));
    }
  }

  private static Path findSchemaPath() {
    Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (directory != null) {
      Path candidate = directory.resolve("deploy/docker-compose/init/mysql/01-schema.sql");
      if (Files.exists(candidate)) {
        return candidate;
      }
      directory = directory.getParent();
    }
    throw new IllegalStateException("Cannot find deploy/docker-compose/init/mysql/01-schema.sql");
  }
}
