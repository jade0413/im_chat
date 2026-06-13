package com.im.conversation.service;

import com.im.proto.body.ReadNotify;
import com.im.proto.rpc.ConnCtx;
import java.util.Collection;

public interface ReadReceiptPusher {

  void pushReadNotify(ConnCtx ctx, Collection<Long> targetUserIds, ReadNotify notify);
}
