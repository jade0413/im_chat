package com.im.message.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpMediaModerationProviderTest {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final ObjectMapper objectMapper = new ObjectMapper();
  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void mapsRevokeResponseToModerationMatchAndSignsRequest() throws Exception {
    AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
    String endpoint = startServer(200,
        "{\"action\":\"REVOKE\",\"provider\":\"aliyun\",\"category\":\"porn\",\"score\":0.91,\"evidence\":\"image hit\"}",
        recorded);
    HttpMediaModerationProvider provider = provider(endpoint, false);

    Optional<MediaModerationMatch> result = provider.scan(request());

    assertThat(result).isPresent();
    MediaModerationMatch match = result.orElseThrow();
    assertThat(match.provider()).isEqualTo("aliyun");
    assertThat(match.category()).isEqualTo("porn");
    assertThat(match.score()).isEqualByComparingTo(new BigDecimal("0.9100"));
    assertThat(match.evidence()).isEqualTo("image hit");
    RecordedRequest sent = recorded.get();
    assertThat(sent.authorization()).isEqualTo("Bearer token-a");
    assertThat(sent.signature()).startsWith("sha256=");
    Map<String, Object> body = objectMapper.readValue(sent.body(), MAP_TYPE);
    assertThat(body).containsEntry("tenantId", 1);
    assertThat(body).containsEntry("messageId", 9003);
    assertThat(body).containsEntry("objectKey", "1/202607/a.png");
    assertThat(body).containsEntry("mediaType", "image");
  }

  @Test
  void returnsEmptyForPassResponse() throws Exception {
    AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
    String endpoint = startServer(200, "{\"action\":\"PASS\",\"score\":0.01}", recorded);

    Optional<MediaModerationMatch> result = provider(endpoint, false).scan(request());

    assertThat(result).isEmpty();
  }

  @Test
  void revokesWhenHitScoreReachesThreshold() throws Exception {
    AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
    String endpoint = startServer(200,
        "{\"hit\":true,\"category\":\"violence\",\"score\":0.95}", recorded);

    Optional<MediaModerationMatch> result = provider(endpoint, false).scan(request());

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().category()).isEqualTo("violence");
  }

  @Test
  void httpFailureIsFailOpenByDefault() throws Exception {
    AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
    String endpoint = startServer(500, "{\"message\":\"downstream error\"}", recorded);

    Optional<MediaModerationMatch> result = provider(endpoint, false).scan(request());

    assertThat(result).isEmpty();
  }

  @Test
  void httpFailureCanFailClosed() throws Exception {
    AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
    String endpoint = startServer(500, "{\"message\":\"downstream error\"}", recorded);

    Optional<MediaModerationMatch> result = provider(endpoint, true).scan(request());

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().category()).isEqualTo("moderation_unavailable");
    assertThat(result.orElseThrow().evidence()).contains("fail_closed:http_status_500");
  }

  private HttpMediaModerationProvider provider(String endpoint, boolean failClosed) {
    return new HttpMediaModerationProvider(
        new ModerationProperties.MediaHttp(
            true,
            endpoint,
            "http_vendor",
            "token-a",
            "secret-a",
            Duration.ofSeconds(2),
            0.9D,
            failClosed),
        objectMapper);
  }

  private MediaModerationRequest request() {
    return new MediaModerationRequest(1L, 9003L, "1/202607/a.png", "image/png", 100L, "image");
  }

  private String startServer(int status, String responseBody, AtomicReference<RecordedRequest> recorded)
      throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/scan", exchange -> handle(exchange, status, responseBody, recorded));
    server.start();
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/scan";
  }

  private void handle(HttpExchange exchange, int status, String responseBody,
      AtomicReference<RecordedRequest> recorded) throws IOException {
    byte[] body = exchange.getRequestBody().readAllBytes();
    recorded.set(new RecordedRequest(
        new String(body, StandardCharsets.UTF_8),
        exchange.getRequestHeaders().getFirst("Authorization"),
        exchange.getRequestHeaders().getFirst("X-IM-Signature")));
    byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, response.length);
    exchange.getResponseBody().write(response);
    exchange.close();
  }

  private record RecordedRequest(String body, String authorization, String signature) {
  }
}
