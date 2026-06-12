package com.im.message.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.message.service.MessageSendResult;
import com.im.message.service.MessageSendService;
import com.im.proto.body.MsgSend;
import com.im.proto.body.MsgSendAck;
import com.im.proto.common.MsgContent;
import com.im.proto.common.TextContent;
import com.im.proto.rpc.ConnCtx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MsgSendHandlerTest {

  @Mock
  private MessageSendService messageSendService;

  @Test
  void returnsMsgSendAckOnSuccess() throws Exception {
    MsgSend request = request("client-1");
    when(messageSendService.send(ctx(), request))
        .thenReturn(new MessageSendResult(9001L, 501L, 7L, 1000L));

    byte[] responseBody = new MsgSendHandler(messageSendService)
        .handle(ctx(), request.toByteArray());

    MsgSendAck ack = MsgSendAck.parseFrom(responseBody);
    assertThat(ack.getCode()).isEqualTo(ErrorCode.OK.code());
    assertThat(ack.getClientMsgId()).isEqualTo("client-1");
    assertThat(ack.getServerMsgId()).isEqualTo(9001L);
    assertThat(ack.getConvId()).isEqualTo(501L);
    assertThat(ack.getSeq()).isEqualTo(7L);
  }

  @Test
  void returnsMsgSendAckForBusinessError() throws Exception {
    MsgSend request = request("client-1");
    when(messageSendService.send(ctx(), request))
        .thenThrow(new ImException(ErrorCode.MSG_TOO_LARGE));

    byte[] responseBody = new MsgSendHandler(messageSendService)
        .handle(ctx(), request.toByteArray());

    MsgSendAck ack = MsgSendAck.parseFrom(responseBody);
    assertThat(ack.getCode()).isEqualTo(ErrorCode.MSG_TOO_LARGE.code());
    assertThat(ack.getClientMsgId()).isEqualTo("client-1");
  }

  @Test
  void rejectsMalformedBody() {
    assertThatThrownBy(() -> new MsgSendHandler(messageSendService).handle(ctx(), new byte[] {1, 2, 3}))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }

  private ConnCtx ctx() {
    return ConnCtx.newBuilder().setTenantId(1L).setUserId(100L).build();
  }

  private MsgSend request(String clientMsgId) {
    return MsgSend.newBuilder()
        .setClientMsgId(clientMsgId)
        .setToUserId(200L)
        .setContent(MsgContent.newBuilder()
            .setText(TextContent.newBuilder().setText("hello")))
        .build();
  }
}
