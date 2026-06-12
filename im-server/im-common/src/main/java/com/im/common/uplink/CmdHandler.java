package com.im.common.uplink;

import com.im.proto.rpc.ConnCtx;

public interface CmdHandler {

  int cmd();

  default int responseCmd() {
    return cmd();
  }

  byte[] handle(ConnCtx ctx, byte[] body);
}
