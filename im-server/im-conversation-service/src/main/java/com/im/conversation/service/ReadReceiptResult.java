package com.im.conversation.service;

import com.im.proto.body.ReadNotify;

public record ReadReceiptResult(ReadNotify readNotify, boolean changed) {
}
