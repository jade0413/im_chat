package com.im.message.moderation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpMediaModerationProvider implements MediaModerationProvider {

  private static final Logger log = LoggerFactory.getLogger(HttpMediaModerationProvider.class);
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final BigDecimal ONE = BigDecimal.ONE.setScale(4);

  private final ModerationProperties.MediaHttp properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public HttpMediaModerationProvider(ModerationProperties.MediaHttp properties,
      ObjectMapper objectMapper) {
    this(properties, objectMapper, HttpClient.newBuilder()
        .connectTimeout(properties.timeout())
        .build());
  }

  HttpMediaModerationProvider(ModerationProperties.MediaHttp properties,
      ObjectMapper objectMapper,
      HttpClient httpClient) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  @Override
  public Optional<MediaModerationMatch> scan(MediaModerationRequest request) {
    if (!properties.isEnabled() || request == null
        || request.objectKey() == null || request.objectKey().isBlank()) {
      return Optional.empty();
    }
    try {
      byte[] body = requestBody(request);
      HttpRequest httpRequest = buildRequest(body);
      HttpResponse<String> response = httpClient.send(
          httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.warn("media moderation http returned non-2xx, status={}, object_key={}",
            response.statusCode(), request.objectKey());
        return failureResult(request, "http_status_" + response.statusCode());
      }
      return parseResponse(request, response.body());
    } catch (Exception ex) {
      log.warn("media moderation http scan failed, object_key={}", request.objectKey(), ex);
      return failureResult(request, ex.getClass().getSimpleName());
    }
  }

  private byte[] requestBody(MediaModerationRequest request) throws JsonProcessingException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("tenantId", request.tenantId());
    body.put("messageId", request.messageId());
    body.put("objectKey", request.objectKey());
    body.put("mime", request.mime());
    body.put("size", request.size());
    body.put("mediaType", request.mediaType());
    return objectMapper.writeValueAsBytes(body);
  }

  private HttpRequest buildRequest(byte[] body) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.endpoint().trim()))
        .timeout(properties.timeout())
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .header("X-IM-Provider", properties.provider())
        .POST(HttpRequest.BodyPublishers.ofByteArray(body));
    if (properties.bearerToken() != null && !properties.bearerToken().isBlank()) {
      builder.header("Authorization", "Bearer " + properties.bearerToken().trim());
    }
    if (properties.hmacSecret() != null && !properties.hmacSecret().isBlank()) {
      String timestamp = Long.toString(Instant.now().getEpochSecond());
      builder.header("X-IM-Timestamp", timestamp);
      builder.header("X-IM-Signature", hmac(timestamp, body));
    }
    return builder.build();
  }

  private Optional<MediaModerationMatch> parseResponse(MediaModerationRequest request, String body)
      throws JsonProcessingException {
    if (body == null || body.isBlank()) {
      return Optional.empty();
    }
    Map<String, Object> json = objectMapper.readValue(body, MAP_TYPE);
    String action = firstText(json, "action", "suggestion", "decision");
    boolean hit = boolValue(json.get("hit"));
    BigDecimal score = decimalValue(json.get("score")).orElse(BigDecimal.ZERO.setScale(4));
    boolean shouldRevoke = isRevokeAction(action)
        || (hit && score.compareTo(BigDecimal.valueOf(properties.revokeThreshold())) >= 0);
    if (!shouldRevoke) {
      return Optional.empty();
    }
    String category = firstText(json, "category", "label", "riskType");
    if (category.isBlank()) {
      category = request.mediaType();
    }
    String evidence = firstText(json, "evidence", "detail", "message");
    if (evidence.isBlank()) {
      evidence = request.mediaType() + ":" + request.objectKey();
    }
    return Optional.of(new MediaModerationMatch(
        textOrDefault(firstText(json, "provider"), properties.provider()),
        category,
        score.compareTo(BigDecimal.ZERO) > 0 ? score : ONE,
        evidence));
  }

  private Optional<MediaModerationMatch> failureResult(MediaModerationRequest request, String reason) {
    if (!properties.failClosed()) {
      return Optional.empty();
    }
    return Optional.of(new MediaModerationMatch(
        properties.provider(),
        "moderation_unavailable",
        ONE,
        "fail_closed:" + reason + ":" + request.mediaType() + ":" + request.objectKey()));
  }

  private boolean isRevokeAction(String action) {
    if (action == null || action.isBlank()) {
      return false;
    }
    return switch (action.trim().toUpperCase(Locale.ROOT)) {
      case "REVOKE", "BLOCK", "REJECT", "DENY" -> true;
      default -> false;
    };
  }

  private String firstText(Map<String, Object> json, String... keys) {
    for (String key : keys) {
      Object value = json.get(key);
      if (value instanceof String text && !text.isBlank()) {
        return text.trim();
      }
    }
    return "";
  }

  private String textOrDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private boolean boolValue(Object value) {
    if (value instanceof Boolean b) {
      return b;
    }
    if (value instanceof String s) {
      return Boolean.parseBoolean(s);
    }
    return false;
  }

  private Optional<BigDecimal> decimalValue(Object value) {
    if (value instanceof Number number) {
      return Optional.of(BigDecimal.valueOf(number.doubleValue()).setScale(4, RoundingMode.HALF_UP));
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return Optional.of(new BigDecimal(text.trim()).setScale(4, RoundingMode.HALF_UP));
      } catch (NumberFormatException ignored) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private String hmac(String timestamp, byte[] body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(
          properties.hmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
      mac.update((byte) '.');
      mac.update(body);
      return "sha256=" + HexFormat.of().formatHex(mac.doFinal());
    } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
      throw new IllegalStateException("cannot sign moderation request", ex);
    }
  }
}
