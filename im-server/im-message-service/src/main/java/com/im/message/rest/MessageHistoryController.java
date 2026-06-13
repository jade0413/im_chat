package com.im.message.rest;

import com.im.common.auth.AuthTokenClaims;
import com.im.common.auth.BearerTokenExtractor;
import com.im.common.auth.JwtAccessTokenVerifier;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.common.web.ApiResponse;
import com.im.message.dto.MessageHistoryResponse;
import com.im.message.dto.MessageItemResponse;
import com.im.message.service.MessagePage;
import com.im.message.service.MessageQueryService;
import com.im.message.service.MessageRevokeService;
import com.im.proto.body.MsgPush;
import com.im.proto.common.MsgStatus;
import com.im.proto.common.RevokeReason;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/convs")
public class MessageHistoryController {

  private final MessageQueryService messageQueryService;
  private final MessageRevokeService messageRevokeService;
  private final JwtAccessTokenVerifier tokenVerifier;

  public MessageHistoryController(MessageQueryService messageQueryService,
      MessageRevokeService messageRevokeService,
      JwtAccessTokenVerifier tokenVerifier) {
    this.messageQueryService = messageQueryService;
    this.messageRevokeService = messageRevokeService;
    this.tokenVerifier = tokenVerifier;
  }

  @GetMapping("/{convId}/messages")
  public ApiResponse<MessageHistoryResponse> history(
      @PathVariable long convId,
      @RequestParam(name = "end_seq", required = false) Long endSeq,
      @RequestParam(name = "limit", defaultValue = "20") int limit,
      @RequestHeader("Authorization") String authorization) {
    AuthTokenClaims claims = verifiedClaims(authorization);
    MessagePage page = messageQueryService.history(claims.userId(), convId, endSeq, limit);
    return ApiResponse.ok(new MessageHistoryResponse(
        convId,
        page.readSeq(),
        page.messages().stream().map(this::toResponse).toList(),
        page.hasMore()));
  }

  @PostMapping("/{convId}/messages/{seq}/revoke")
  public ApiResponse<Void> revoke(
      @PathVariable long convId,
      @PathVariable long seq,
      @RequestHeader("Authorization") String authorization) {
    AuthTokenClaims claims = verifiedClaims(authorization);
    messageRevokeService.revoke(convId, seq, RevokeReason.BY_SENDER, claims.userId());
    return ApiResponse.ok(null);
  }

  private AuthTokenClaims verifiedClaims(String authorization) {
    AuthTokenClaims claims = tokenVerifier.verifyAccessToken(BearerTokenExtractor.extract(authorization));
    if (claims.tenantId() != TenantContext.requiredTenantId()) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    return claims;
  }

  private MessageItemResponse toResponse(MsgPush push) {
    return new MessageItemResponse(
        push.getConvId(),
        push.getSeq(),
        push.getServerMsgId(),
        push.getClientMsgId(),
        push.getSender().getUserId(),
        push.getSendTime(),
        push.getContent().hasText() ? 1 : 0,
        intExt(push, "status", MsgStatus.NORMAL.getNumber()),
        intExt(push, "revoke_reason", RevokeReason.REVOKE_REASON_UNSPECIFIED.getNumber()),
        push.getContent().hasText() ? push.getContent().getText().getText() : "");
  }

  private int intExt(MsgPush push, String key, int fallback) {
    String value = push.getExtOrDefault(key, "");
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }
}
