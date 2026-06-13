package com.im.common.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtAccessTokenVerifier {

  private static final String ACCESS_TOKEN = "access";
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
  };

  private final String secret;
  private final String issuer;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final Base64.Encoder base64UrlEncoder = Base64.getUrlEncoder().withoutPadding();
  private final Base64.Decoder base64UrlDecoder = Base64.getUrlDecoder();

  @Autowired
  public JwtAccessTokenVerifier(
      @Value("${im.auth.jwt.secret:dev-only-change-me-please-dev-only-change-me}") String secret,
      @Value("${im.auth.jwt.issuer:im-server}") String issuer,
      ObjectMapper objectMapper) {
    this(secret, issuer, objectMapper, Clock.systemUTC());
  }

  JwtAccessTokenVerifier(String secret, String issuer, ObjectMapper objectMapper, Clock clock) {
    this.secret = secret;
    this.issuer = issuer;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public AuthTokenClaims verifyAccessToken(String token) {
    String[] parts = token == null ? new String[0] : token.split("\\.");
    if (parts.length != 3) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }

    String signingInput = parts[0] + "." + parts[1];
    String expectedSignature = sign(signingInput);
    if (!MessageDigest.isEqual(
        expectedSignature.getBytes(StandardCharsets.UTF_8),
        parts[2].getBytes(StandardCharsets.UTF_8))) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }

    Map<String, Object> payload = decodeJson(parts[1]);
    if (!issuer.equals(stringClaim(payload, "iss"))) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    if (!ACCESS_TOKEN.equals(stringClaim(payload, "typ"))) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }

    Instant expiresAt = Instant.ofEpochSecond(longClaim(payload, "exp"));
    if (!expiresAt.isAfter(clock.instant())) {
      throw new ImException(ErrorCode.TOKEN_EXPIRED);
    }
    String platformClass = (String) payload.get("platform_class");
    long tokenVersion = payload.containsKey("token_ver") ? longClaim(payload, "token_ver") : 0L;
    return new AuthTokenClaims(
        longClaim(payload, "tenant_id"),
        Long.parseLong(stringClaim(payload, "sub")),
        expiresAt,
        platformClass,
        tokenVersion);
  }

  private Map<String, Object> decodeJson(String encodedPayload) {
    try {
      return objectMapper.readValue(base64UrlDecoder.decode(encodedPayload), MAP_TYPE);
    } catch (Exception ex) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
  }

  private String sign(String signingInput) {
    if (secret == null || secret.length() < 32) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "JWT secret must contain at least 32 characters");
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return base64UrlEncoder.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "failed to verify token");
    }
  }

  private String stringClaim(Map<String, Object> payload, String name) {
    Object value = payload.get(name);
    if (value == null) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    return value.toString();
  }

  private long longClaim(Map<String, Object> payload, String name) {
    Object value = payload.get(name);
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(stringClaim(payload, name));
    } catch (NumberFormatException ex) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
  }
}
