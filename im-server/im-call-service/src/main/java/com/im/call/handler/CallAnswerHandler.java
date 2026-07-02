package com.im.call.handler;

import com.google.protobuf.InvalidProtocolBufferException;
import com.im.call.service.CallService;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.uplink.CmdHandler;
import com.im.proto.body.CallAnswer;
import com.im.proto.rpc.ConnCtx;
import com.im.proto.ws.Cmd;
import org.springframework.stereotype.Service;

@Service
public class CallAnswerHandler implements CmdHandler {

  private final CallService callService;

  public CallAnswerHandler(CallService callService) {
    this.callService = callService;
  }

  @Override
  public int cmd() {
    return Cmd.CALL_ANSWER_VALUE;
  }

  @Override
  public int responseCmd() {
    return Cmd.CALL_ACK_VALUE;
  }

  @Override
  public byte[] handle(ConnCtx ctx, byte[] body) {
    try {
      return callService.answer(ctx, CallAnswer.parseFrom(body)).toByteArray();
    } catch (InvalidProtocolBufferException ex) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "invalid CallAnswer body", ex);
    }
  }
}
