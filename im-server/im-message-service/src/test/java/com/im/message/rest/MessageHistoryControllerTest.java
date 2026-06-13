package com.im.message.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.im.common.auth.UserContext;
import com.im.common.web.ApiResponse;
import com.im.message.dto.MessageHistoryResponse;
import com.im.message.service.MessagePage;
import com.im.message.service.MessageQueryService;
import com.im.message.service.MessageRevokeService;
import com.im.proto.body.MsgPush;
import com.im.proto.body.Sender;
import com.im.proto.common.MsgContent;
import com.im.proto.common.MsgStatus;
import com.im.proto.common.RevokeReason;
import com.im.proto.common.TextContent;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * MessageHistoryController 单元测试。
 * 跨租户 / token 验证由 JwtAuthInterceptor 负责，此处只测 Controller 行为。
 */
@ExtendWith(MockitoExtension.class)
class MessageHistoryControllerTest {

  @Mock
  private MessageQueryService messageQueryService;

  @Mock
  private MessageRevokeService messageRevokeService;

  @BeforeEach
  void setUp() {
    UserContext.set(100L);
  }

  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  @Test
  void returnsHistoryForCurrentUser() {
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

    ApiResponse<MessageHistoryResponse> response = controller().history(501L, 9L, 20);

    assertThat(response.data().messages()).hasSize(1);
    assertThat(response.data().readSeq()).isEqualTo(8L);
    assertThat(response.data().messages().getFirst().text()).isEqualTo("hello");
    assertThat(response.data().messages().getFirst().status()).isEqualTo(MsgStatus.NORMAL.getNumber());
    assertThat(response.data().hasMore()).isFalse();
  }

  @Test
  void returnsRevokedStatusInHistory() {
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

    ApiResponse<MessageHistoryResponse> response = controller().history(501L, 9L, 20);

    assertThat(response.data().messages().getFirst().text()).isEmpty();
    assertThat(response.data().messages().getFirst().status()).isEqualTo(MsgStatus.REVOKED.getNumber());
    assertThat(response.data().messages().getFirst().revokeReason()).isEqualTo(
        RevokeReason.BY_SENDER.getNumber());
  }

  @Test
  void revokesMessageForCurrentUser() {
    controller().revoke(501L, 9L);

    verify(messageRevokeService).revoke(501L, 9L, RevokeReason.BY_SENDER, 100L);
  }

  private MessageHistoryController controller() {
    return new MessageHistoryController(messageQueryService, messageRevokeService);
  }
}
