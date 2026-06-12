package com.im.message.handler;

import com.google.protobuf.InvalidProtocolBufferException;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.uplink.CmdHandler;
import com.im.message.service.MessageQueryService;
import com.im.proto.body.SyncReq;
import com.im.proto.rpc.ConnCtx;
import com.im.proto.ws.Cmd;
import org.springframework.stereotype.Service;

@Service
public class SyncReqHandler implements CmdHandler {

  private final MessageQueryService messageQueryService;

  public SyncReqHandler(MessageQueryService messageQueryService) {
    this.messageQueryService = messageQueryService;
  }

  @Override
  public int cmd() {
    return Cmd.SYNC_REQ_VALUE;
  }

  @Override
  public int responseCmd() {
    return Cmd.SYNC_RESP_VALUE;
  }

  @Override
  public byte[] handle(ConnCtx ctx, byte[] body) {
    try {
      return messageQueryService.sync(ctx.getUserId(), SyncReq.parseFrom(body)).toByteArray();
    } catch (InvalidProtocolBufferException ex) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "invalid SyncReq body", ex);
    }
  }
}
