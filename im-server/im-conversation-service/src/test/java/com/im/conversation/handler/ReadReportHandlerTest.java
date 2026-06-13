package com.im.conversation.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.conversation.service.ReadReceiptResult;
import com.im.conversation.service.ReadReceiptService;
import com.im.proto.body.ReadNotify;
import com.im.proto.body.ReadReport;
import com.im.proto.rpc.ConnCtx;
import com.im.proto.ws.Cmd;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadReportHandlerTest {

  @Mock
  private ReadReceiptService readReceiptService;

  @Test
  void handlesReadReportAndReturnsReadNotify() throws Exception {
    ConnCtx ctx = ctx();
    ReadReport request = ReadReport.newBuilder()
        .setConvId(501L)
        .setReadSeq(2L)
        .build();
    ReadNotify notify = ReadNotify.newBuilder()
        .setConvId(501L)
        .setReaderUserId(100L)
        .setReadSeq(2L)
        .build();
    when(readReceiptService.reportRead(ctx, request)).thenReturn(new ReadReceiptResult(notify, true));

    ReadReportHandler handler = new ReadReportHandler(readReceiptService);
    byte[] response = handler.handle(ctx, request.toByteArray());

    assertThat(handler.cmd()).isEqualTo(Cmd.READ_REPORT_VALUE);
    assertThat(handler.responseCmd()).isEqualTo(Cmd.READ_NOTIFY_VALUE);
    assertThat(ReadNotify.parseFrom(response)).isEqualTo(notify);
  }

  @Test
  void rejectsInvalidBody() {
    ReadReportHandler handler = new ReadReportHandler(readReceiptService);

    assertThatThrownBy(() -> handler.handle(ctx(), new byte[] {1, 2, 3}))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }

  private ConnCtx ctx() {
    return ConnCtx.newBuilder()
        .setTenantId(1L)
        .setUserId(100L)
        .setPlatform(1)
        .setDeviceId("device-a")
        .setConnId("conn-a")
        .setGwInstance("gw-a")
        .build();
  }
}
