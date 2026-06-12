package com.im.push.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisOnlineRouteRepositoryTest {

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  private ObjectMapper objectMapper;
  private RedisOnlineRouteRepository repository;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    repository = new RedisOnlineRouteRepository(redisTemplate, objectMapper);
  }

  @Test
  void batchFindReadsKnownPlatformKeysWithoutRedisKeysScan() throws Exception {
    OnlineRoute mobile = route(100L, 1, "mobile", "conn-mobile");
    OnlineRoute desktop = route(200L, 3, "desktop", "conn-desktop");
    List<String> keys = List.of(
        "route:1:100:mobile",
        "route:1:100:desktop",
        "route:1:100:web",
        "route:1:200:mobile",
        "route:1:200:desktop",
        "route:1:200:web");
    when(valueOperations.multiGet(keys)).thenReturn(Arrays.asList(
        objectMapper.writeValueAsString(mobile),
        null,
        "",
        null,
        objectMapper.writeValueAsString(desktop),
        null));

    List<OnlineRoute> routes = repository.findAllByUsers(1L,
        Arrays.asList(100L, 100L, null, -1L, 200L));

    assertThat(routes).containsExactly(mobile, desktop);
    verify(valueOperations).multiGet(keys);
    verify(redisTemplate, never()).keys(anyString());
  }

  private OnlineRoute route(long userId, int platform, String platformClass, String connId) {
    return new OnlineRoute(1L, userId, platform, platformClass,
        "device-" + userId, connId, "gw-a");
  }
}
