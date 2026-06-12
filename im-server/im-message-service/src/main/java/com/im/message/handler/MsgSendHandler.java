package com.im.message.handler;

import com.google.protobuf.InvalidProtocolBufferException;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.uplink.CmdHandler;
import com.im.message.service.MessageSendResult;
import com.im.message.service.MessageSendService;
import com.im.proto.body.MsgSend;
import com.im.proto.body.MsgSendAck;
import com.im.proto.rpc.ConnCtx;
import com.im.proto.ws.Cmd;
import org.springframework.stereotype.Service;

@Service
public class MsgSendHandler implements CmdHandler {

  private final MessageSendService messageSendService;

  public MsgSendHandler(MessageSendService messageSendService) {
    this.messageSendService = messageSendService;
  }

  @Override
  public int cmd() {
    return Cmd.MSG_SEND_VALUE;
  }

  @Override
  public int responseCmd() {
    return Cmd.MSG_SEND_ACK_VALUE;
  }

  @Override
  public byte[] handle(ConnCtx ctx, byte[] body) {
    MsgSend request = parse(body);
    try {
      MessageSendResult result = messageSendService.send(ctx, request);
      return MsgSendAck.newBuilder()
          .setCode(ErrorCode.OK.code())
          .setClientMsgId(request.getClientMsgId())
          .setServerMsgId(result.serverMsgId())
          .setConvId(result.conversationId())
          .setSeq(result.seq())
          .setServerTime(result.serverTimeMillis())
          .build()
          .toByteArray();
    } catch (ImException ex) {
      return MsgSendAck.newBuilder()
          .setCode(ex.errorCode().code())
          .setClientMsgId(request.getClientMsgId())
          .build()
          .toByteArray();
    }
  }

  private MsgSend parse(byte[] body) {
    try {
      return MsgSend.parseFrom(body);
    } catch (InvalidProtocolBufferException ex) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "invalid MsgSend body", ex);
    }
  }
}
