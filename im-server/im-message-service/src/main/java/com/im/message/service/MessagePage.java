package com.im.message.service;

import com.im.proto.body.MsgPush;
import java.util.List;

public record MessagePage(List<MsgPush> messages, boolean hasMore, long readSeq) {
}
