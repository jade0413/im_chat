package com.im.message.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.im.common.auth.AuthTokenClaims;
import com.im.common.auth.JwtAccessTokenVerifier;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.common.web.ApiResponse;
import com.im.message.dto.MessageHistoryResponse;
import com.im.message.service.MessagePage;
import com.im.message.service.MessageQueryService;
import com.im.message.service.MessageRevokeService;
import com.im.proto.body.MsgPush;
import com.im.proto.body.Sender;
import com.im.proto.common.MsgStatus;
import com.im.proto.common.MsgContent;
import com.im.proto.common.RevokeReason;
import com.im.proto.common.TextContent;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageHistoryControllerTest {

  @Mock
  private MessageQueryService messageQueryService;

  @Mock
  private MessageRevokeService messageRevokeService;

  @Mock
  private JwtAccessTokenVerifier tokenVerifier;

  @Test
  void returnsHistoryForAuthorizedUser() {
    when(tokenVerifier.verifyAccessToken("token"))
        .thenReturn(new AuthTokenClaims(1L, 100L, Instant.now().plusSeconds(3600)));
    when(messageQueryService.history(100L, 501L, 9L, 20)).thenReturn(new MessagePage(List.of(
        MsgPush.newBuilder()
            .setConvId(501L)
            .setSeq(9L)
            .setServerMsgId(9009L)
            .setClientMsgId("client-9")
            .setSender(Sender.newBuilder().setUserId(100L))
            .setSendTime(1000L)
            .setContent(MsgContent.newBuilder()
                .setText(TextContent.newBuilder().setText("hello")))
            .putExt("status", Integer.toString(MsgStatus.NORMAL.getNumber()))
            .build()), false, 8L));

    ApiResponse<MessageHistoryResponse> response = historyWithTenant();

    assertThat(response.data().messages()).hasSize(1);
    assertThat(response.data().readSeq()).isEqualTo(8L);
    assertThat(response.data().messages().getFirst().text()).isEqualTo("hello");
    assertThat(response.data().messages().getFirst().status()).isEqualTo(MsgStatus.NORMAL.getNumber());
    assertThat(response.data().hasMore()).isFalse();
  }

  @Test
  void returnsRevokedStatusInHistory() {
    when(tokenVerifier.verifyAccessToken("token"))
        .thenReturn(new AuthTokenClaims(1L, 100L, Instant.now().plusSeconds(3600)));
    when(messageQueryService.history(100L, 501L, 9L, 20)).thenReturn(new MessagePage(List.of(
        MsgPush.newBuilder()
            .setConvId(501L)
            .setSeq(9L)
            .setServerMsgId(9009L)
            .setSender(Sender.newBuilder().setUserId(100L))
            .setSendTime(1000L)
            .putExt("status", Integer.toString(MsgStatus.REVOKED.getNumber()))
            .putExt("revoke_reason", Integer.toString(RevokeReason.BY_SENDER.getNumber()))
            .build()), false, 8L));

    ApiResponse<MessageHistoryResponse> response = historyWithTenant();

    assertThat(response.data().messages().getFirst().text()).isEmpty();
    assertThat(response.data().messages().getFirst().status()).isEqualTo(MsgStatus.REVOKED.getNumber());
    assertThat(response.data().messages().getFirst().revokeReason()).isEqualTo(
        RevokeReason.BY_SENDER.getNumber());
  }

  @Test
  void revokesMessageForAuthorizedSender() {
    when(tokenVerifier.verifyAccessToken("token"))
        .thenReturn(new AuthTokenClaims(1L, 100L, Instant.now().plusSeconds(3600)));

    TenantContext.runWithTenant(1L, () -> new MessageHistoryController(
        messageQueryService,
        messageRevokeService,
        tokenVerifier).revoke(501L, 9L, "Bearer token"));

    verify(messageRevokeService).revoke(501L, 9L, RevokeReason.BY_SENDER, 100L);
  }

  @Test
  void rejectsCrossTenantToken() {
    when(tokenVerifier.verifyAccessToken("token"))
        .thenReturn(new AuthTokenClaims(2L, 100L, Instant.now().plusSeconds(3600)));

    assertThatThrownBy(this::historyWithTenant)
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.TOKEN_INVALID);
  }

  private ApiResponse<MessageHistoryResponse> historyWithTenant() {
    AtomicReference<ApiResponse<MessageHistoryResponse>> result = new AtomicReference<>();
    TenantContext.runWithTenant(1L, () -> result.set(new MessageHistoryController(
        messageQueryService,
        messageRevokeService,
        tokenVerifier).history(501L, 9L, 20, "Bearer token")));
    return result.get();
  }
}
