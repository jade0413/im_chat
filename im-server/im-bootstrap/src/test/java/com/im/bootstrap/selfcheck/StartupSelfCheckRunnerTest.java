package com.im.bootstrap.selfcheck;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.im.common.id.WorkerIdLease;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class StartupSelfCheckRunnerTest {

  @Mock
  private JdbcTemplate jdbcTemplate;

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private RedisConnectionFactory redisConnectionFactory;

  @Mock
  private RedisConnection redisConnection;

  @Mock
  private AmqpAdmin amqpAdmin;

  @Mock
  private ObjectProvider<WorkerIdLease> workerIdLease;

  private TopicExchange exchange;

  @BeforeEach
  void setUp() {
    exchange = new TopicExchange("im.events");
  }

  @Test
  void passesWhenEnabledChecksSucceed() {
    StartupSelfCheckRunner runner = runner(List.of("tenant", "message"));
    when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("tenant"))).thenReturn(1);
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("message"))).thenReturn(1);
    when(redisTemplate.getConnectionFactory()).thenReturn(redisConnectionFactory);
    when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
    when(redisConnection.ping()).thenReturn("PONG");

    runner.run(new DefaultApplicationArguments());

    verify(amqpAdmin).declareExchange(exchange);
  }

  @Test
  void failsWhenRequiredTableIsMissing() {
    StartupSelfCheckRunner runner = runner(List.of("tenant", "message"));
    when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("tenant"))).thenReturn(1);
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("message"))).thenReturn(0);

    assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
        .hasMessageContaining("mysql required table missing: message");
  }

  private StartupSelfCheckRunner runner(List<String> requiredTables) {
    StartupSelfCheckProperties properties = new StartupSelfCheckProperties(
        true,
        true,
        true,
        true,
        false,
        requiredTables,
        new StartupSelfCheckProperties.Minio(null, null, null, "im-media"));
    return new StartupSelfCheckRunner(properties, jdbcTemplate, redisTemplate,
        amqpAdmin, exchange, workerIdLease);
  }
}
