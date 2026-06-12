package com.im.message.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.message.service.MessageQueryService;
import com.im.proto.body.SyncReq;
import com.im.proto.body.SyncResp;
import com.im.proto.rpc.ConnCtx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncReqHandlerTest {

  @Mock
  private MessageQueryService messageQueryService;

  @Test
  void returnsSyncRespBody() throws Exception {
    ConnCtx ctx = ConnCtx.newBuilder().setTenantId(1L).setUserId(100L).build();
    SyncReq request = SyncReq.newBuilder()
        .addConvVersions(SyncReq.ConvVersion.newBuilder().setConvId(501L).setLocalMaxSeq(1L))
        .build();
    SyncResp response = SyncResp.newBuilder().setConvListVersion(10L).build();
    when(messageQueryService.sync(100L, request)).thenReturn(response);

    byte[] body = new SyncReqHandler(messageQueryService).handle(ctx, request.toByteArray());

    assertThat(SyncResp.parseFrom(body).getConvListVersion()).isEqualTo(10L);
  }

  @Test
  void rejectsMalformedBody() {
    ConnCtx ctx = ConnCtx.newBuilder().setTenantId(1L).setUserId(100L).build();

    assertThatThrownBy(() -> new SyncReqHandler(messageQueryService).handle(ctx, new byte[] {1, 2}))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }
}
