package com.im.message.service;

import com.im.proto.body.ConvInfo;
import com.im.proto.body.MsgSend;
import com.im.proto.rpc.ConnCtx;

public interface ConversationResolver {

  ConvInfo resolve(ConnCtx ctx, MsgSend request);
}
