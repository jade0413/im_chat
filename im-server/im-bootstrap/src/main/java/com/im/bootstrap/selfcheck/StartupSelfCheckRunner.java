package com.im.bootstrap.selfcheck;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.id.WorkerIdLease;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "im.startup-check", name = "enabled", havingValue = "true")
public class StartupSelfCheckRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(StartupSelfCheckRunner.class);

  private final StartupSelfCheckProperties properties;
  private final JdbcTemplate jdbcTemplate;
  private final StringRedisTemplate redisTemplate;
  private final AmqpAdmin amqpAdmin;
  private final TopicExchange imEventsExchange;
  private final ObjectProvider<WorkerIdLease> workerIdLease;

  public StartupSelfCheckRunner(StartupSelfCheckProperties properties,
      JdbcTemplate jdbcTemplate,
      StringRedisTemplate redisTemplate,
      AmqpAdmin amqpAdmin,
      TopicExchange imEventsExchange,
      ObjectProvider<WorkerIdLease> workerIdLease) {
    this.properties = properties;
    this.jdbcTemplate = jdbcTemplate;
    this.redisTemplate = redisTemplate;
    this.amqpAdmin = amqpAdmin;
    this.imEventsExchange = imEventsExchange;
    this.workerIdLease = workerIdLease;
  }

  @Override
  public void run(ApplicationArguments args) {
    log.info("startup self-check started");
    if (properties.mysql()) {
      checkMysql();
    }
    if (properties.redis()) {
      checkRedis();
    }
    if (properties.rabbitmq()) {
      checkRabbitMq();
    }
    if (properties.minio()) {
      checkMinio();
    }
    if (workerIdLease.getIfAvailable() != null) {
      log.info("startup self-check passed: snowflake worker_id lease acquired");
    }
    log.info("startup self-check finished");
  }

  private void checkMysql() {
    Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    if (!Integer.valueOf(1).equals(one)) {
      throw failed("mysql SELECT 1 returned unexpected value");
    }
    for (String table : properties.requiredTables()) {
      Integer exists = jdbcTemplate.queryForObject("""
          SELECT COUNT(*)
          FROM information_schema.tables
          WHERE table_schema = DATABASE()
            AND table_name = ?
          """, Integer.class, table);
      if (!Integer.valueOf(1).equals(exists)) {
        throw failed("mysql required table missing: " + table);
      }
    }
    log.info("startup self-check passed: mysql tables={}", properties.requiredTables());
  }

  private void checkRedis() {
    RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
    if (connectionFactory == null) {
      throw failed("redis connection factory is unavailable");
    }
    try (RedisConnection connection = connectionFactory.getConnection()) {
      String pong = connection.ping();
      if (!"PONG".equalsIgnoreCase(pong)) {
        throw failed("redis PING returned unexpected value");
      }
    }
    log.info("startup self-check passed: redis ping");
  }

  private void checkRabbitMq() {
    amqpAdmin.declareExchange(imEventsExchange);
    log.info("startup self-check passed: rabbitmq exchange={}", imEventsExchange.getName());
  }

  private void checkMinio() {
    StartupSelfCheckProperties.Minio minio = properties.minioConfig();
    requireText(minio.endpoint(), "minio endpoint is required");
    requireText(minio.accessKey(), "minio access key is required");
    requireText(minio.secretKey(), "minio secret key is required");
    requireText(minio.bucket(), "minio bucket is required");
    try {
      boolean exists = MinioClient.builder()
          .endpoint(minio.endpoint())
          .credentials(minio.accessKey(), minio.secretKey())
          .build()
          .bucketExists(BucketExistsArgs.builder().bucket(minio.bucket()).build());
      if (!exists) {
        throw failed("minio bucket missing: " + minio.bucket());
      }
      log.info("startup self-check passed: minio bucket={}", minio.bucket());
    } catch (ImException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "minio self-check failed", ex);
    }
  }

  private void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw failed(message);
    }
  }

  private ImException failed(String message) {
    return new ImException(ErrorCode.INTERNAL_ERROR, "startup self-check failed: " + message);
  }
}
