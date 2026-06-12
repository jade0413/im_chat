package com.im.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.user.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  public static final String ACCESS_TOKEN = "access";
  public static final String REFRESH_TOKEN = "refresh";

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
  };

  private final AuthProperties authProperties;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final Base64.Encoder base64UrlEncoder = Base64.getUrlEncoder().withoutPadding();
  private final Base64.Decoder base64UrlDecoder = Base64.getUrlDecoder();

  @Autowired
  public JwtService(AuthProperties authProperties, ObjectMapper objectMapper) {
    this(authProperties, objectMapper, Clock.systemUTC());
  }

  JwtService(AuthProperties authProperties, ObjectMapper objectMapper, Clock clock) {
    this.authProperties = authProperties;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public String createAccessToken(long tenantId, long userId) {
    return createToken(tenantId, userId, ACCESS_TOKEN,
        clock.instant().plus(authProperties.jwt().accessTtl()));
  }

  public String createRefreshToken(long tenantId, long userId) {
    return createToken(tenantId, userId, REFRESH_TOKEN,
        clock.instant().plus(authProperties.jwt().refreshTtl()));
  }

  public TokenClaims verifyAccessToken(String token) {
    return verifyToken(token, ACCESS_TOKEN);
  }

  public TokenClaims verifyRefreshToken(String token) {
    return verifyToken(token, REFRESH_TOKEN);
  }

  public long accessExpiresInSeconds() {
    return authProperties.jwt().accessTtl().toSeconds();
  }

  public long refreshExpiresInSeconds() {
    return authProperties.jwt().refreshTtl().toSeconds();
  }

  private String createToken(long tenantId, long userId, String tokenType, Instant expiresAt) {
    Map<String, Object> header = new LinkedHashMap<>();
    header.put("alg", "HS256");
    header.put("typ", "JWT");

    Instant issuedAt = clock.instant();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("iss", authProperties.jwt().issuer());
    payload.put("sub", Long.toString(userId));
    payload.put("tenant_id", tenantId);
    payload.put("typ", tokenType);
    payload.put("iat", issuedAt.getEpochSecond());
    payload.put("exp", expiresAt.getEpochSecond());

    String encodedHeader = encodeJson(header);
    String encodedPayload = encodeJson(payload);
    String signingInput = encodedHeader + "." + encodedPayload;
    return signingInput + "." + sign(signingInput);
  }

  private TokenClaims verifyToken(String token, String expectedType) {
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
    String issuer = stringClaim(payload, "iss");
    if (!authProperties.jwt().issuer().equals(issuer)) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }

    String tokenType = stringClaim(payload, "typ");
    if (!expectedType.equals(tokenType)) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }

    Instant expiresAt = Instant.ofEpochSecond(longClaim(payload, "exp"));
    if (!expiresAt.isAfter(clock.instant())) {
      throw new ImException(ErrorCode.TOKEN_EXPIRED);
    }

    return new TokenClaims(
        longClaim(payload, "tenant_id"),
        Long.parseLong(stringClaim(payload, "sub")),
        tokenType,
        expiresAt);
  }

  private String encodeJson(Map<String, Object> value) {
    try {
      return base64UrlEncoder.encodeToString(objectMapper.writeValueAsBytes(value));
    } catch (Exception ex) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "failed to create token");
    }
  }

  private Map<String, Object> decodeJson(String encodedPayload) {
    try {
      return objectMapper.readValue(base64UrlDecoder.decode(encodedPayload), MAP_TYPE);
    } catch (Exception ex) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
  }

  private String sign(String signingInput) {
    String secret = authProperties.jwt().secret();
    if (secret == null || secret.length() < 32) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "JWT secret must contain at least 32 characters");
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return base64UrlEncoder.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "failed to sign token");
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
