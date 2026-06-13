package com.im.bootstrap.e2e;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Comparator;
import java.util.stream.Stream;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

final class SchemaScripts {

  private SchemaScripts() {
  }

  static void applyInitialSchema(Connection connection) {
    ScriptUtils.executeSqlScript(
        connection,
        new EncodedResource(new FileSystemResource(findSchemaPath()), StandardCharsets.UTF_8));
    applyMigrations(connection);
  }

  static void applyInitialSchema(Connection connection, String database) throws Exception {
    String schema = Files.readString(findSchemaPath(), StandardCharsets.UTF_8)
        .replace("USE im;", "USE `" + database + "`;");
    ScriptUtils.executeSqlScript(
        connection,
        new EncodedResource(new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
    applyMigrations(connection);
  }

  private static void applyMigrations(Connection connection) {
    Path migrationDirectory = findMigrationDirectory();
    try (Stream<Path> migrations = Files.list(migrationDirectory)) {
      migrations
          .filter(path -> path.getFileName().toString().matches("V\\d+__.*\\.sql"))
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .forEach(path -> executeMigration(connection, path));
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to apply test migrations", ex);
    }
  }

  private static void executeMigration(Connection connection, Path path) {
    try {
      ScriptUtils.executeSqlScript(
          connection,
          new EncodedResource(new FileSystemResource(path), StandardCharsets.UTF_8));
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to apply migration " + path.getFileName(), ex);
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

  private static Path findMigrationDirectory() {
    Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (directory != null) {
      Path candidate = directory.resolve("im-server/im-bootstrap/src/main/resources/db/migration");
      if (Files.isDirectory(candidate)) {
        return candidate;
      }
      directory = directory.getParent();
    }
    throw new IllegalStateException("Cannot find im-server/im-bootstrap/src/main/resources/db/migration");
  }
}
