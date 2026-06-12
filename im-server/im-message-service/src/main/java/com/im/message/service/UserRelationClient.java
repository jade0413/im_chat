package com.im.message.service;

public interface UserRelationClient {

  void ensureCanSendC2c(long fromUserId, long toUserId);
}
