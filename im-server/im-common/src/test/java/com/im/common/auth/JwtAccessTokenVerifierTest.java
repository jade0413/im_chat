package com.im.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class JwtAccessTokenVerifierTest {

  private static final String SECRET = "test-secret-test-secret-test-secret-32";
  private static final Instant NOW = Instant.parse("2026-06-13T00:00:00Z");

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JwtAccessTokenVerifier verifier = new JwtAccessTokenVerifier(
      SECRET,
      "im-server",
      objectMapper,
      Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void verifiesAccessToken() {
    AuthTokenClaims claims = verifier.verifyAccessToken(token("access", NOW.plusSeconds(3600), SECRET));

    assertThat(claims.tenantId()).isEqualTo(1L);
    assertThat(claims.userId()).isEqualTo(100L);
  }

  @Test
  void rejectsExpiredToken() {
    assertThatThrownBy(() -> verifier.verifyAccessToken(token("access", NOW.minusSeconds(1), SECRET)))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.TOKEN_EXPIRED);
  }

  @Test
  void rejectsWrongType() {
    assertThatThrownBy(() -> verifier.verifyAccessToken(token("refresh", NOW.plusSeconds(3600), SECRET)))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.TOKEN_INVALID);
  }

  @Test
  void rejectsBadSignature() {
    assertThatThrownBy(() -> verifier.verifyAccessToken(token("access", NOW.plusSeconds(3600), SECRET + "x")))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.TOKEN_INVALID);
  }

  private String token(String type, Instant expiresAt, String secret) {
    Map<String, Object> header = new LinkedHashMap<>();
    header.put("alg", "HS256");
    header.put("typ", "JWT");

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("iss", "im-server");
    payload.put("sub", "100");
    payload.put("tenant_id", 1L);
    payload.put("typ", type);
    payload.put("iat", NOW.getEpochSecond());
    payload.put("exp", expiresAt.getEpochSecond());

    String encodedHeader = encode(header);
    String encodedPayload = encode(payload);
    String input = encodedHeader + "." + encodedPayload;
    return input + "." + sign(input, secret);
  }

  private String encode(Map<String, Object> value) {
    try {
      return Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(value));
    } catch (Exception ex) {
      throw new AssertionError(ex);
    }
  }

  private String sign(String input, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new AssertionError(ex);
    }
  }
}
