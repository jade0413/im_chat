package com.im.conversation.handler;

import com.google.protobuf.InvalidProtocolBufferException;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.uplink.CmdHandler;
import com.im.conversation.service.ReadReceiptService;
import com.im.proto.body.ReadReport;
import com.im.proto.rpc.ConnCtx;
import com.im.proto.ws.Cmd;
import org.springframework.stereotype.Service;

@Service
public class ReadReportHandler implements CmdHandler {

  private final ReadReceiptService readReceiptService;

  public ReadReportHandler(ReadReceiptService readReceiptService) {
    this.readReceiptService = readReceiptService;
  }

  @Override
  public int cmd() {
    return Cmd.READ_REPORT_VALUE;
  }

  @Override
  public int responseCmd() {
    return Cmd.READ_NOTIFY_VALUE;
  }

  @Override
  public byte[] handle(ConnCtx ctx, byte[] body) {
    try {
      return readReceiptService.reportRead(ctx, ReadReport.parseFrom(body))
          .readNotify()
          .toByteArray();
    } catch (InvalidProtocolBufferException ex) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "invalid ReadReport body", ex);
    }
  }
}
