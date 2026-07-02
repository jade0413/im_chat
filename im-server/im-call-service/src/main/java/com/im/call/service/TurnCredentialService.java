package com.im.call.service;

import com.im.call.config.CallProperties;
import com.im.proto.body.IceServer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * coturn REST 凭证（RFC "TURN REST API" / coturn use-auth-secret 模式，D45）。
 *
 * <p>username = {unixExpiry}:{tenantId}-{userId}，
 * credential = base64(HMAC-SHA1(static-auth-secret, username))。
 * coturn 用同一 secret 验证，无需与业务侧共享用户库。
 */
@Service
public class TurnCredentialService {

  private static final String HMAC_SHA1 = "HmacSHA1";

  private final CallProperties properties;

  public TurnCredentialService(CallProperties properties) {
    this.properties = properties;
  }

  /** 组装下发给客户端的 ICE server 列表：STUN（无凭证）+ TURN（时效凭证）。 */
  public List<IceServer> iceServersFor(long tenantId, long userId) {
    List<IceServer> servers = new ArrayList<>(2);
    if (!properties.stunUrls().isEmpty()) {
      servers.add(IceServer.newBuilder().addAllUrls(properties.stunUrls()).build());
    }
    if (!properties.turnUrls().isEmpty() && !properties.turnSecret().isBlank()) {
      long expiry = System.currentTimeMillis() / 1000
          + properties.turnCredentialTtl().toSeconds();
      String username = expiry + ":" + tenantId + "-" + userId;
      servers.add(IceServer.newBuilder()
          .addAllUrls(properties.turnUrls())
          .setUsername(username)
          .setCredential(hmacSha1Base64(properties.turnSecret(), username))
          .build());
    }
    return servers;
  }

  private static String hmacSha1Base64(String secret, String payload) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA1);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA1));
      return Base64.getEncoder()
          .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("failed to sign turn credential", ex);
    }
  }
}
