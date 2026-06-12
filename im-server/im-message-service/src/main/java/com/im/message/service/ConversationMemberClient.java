package com.im.message.service;

import java.util.List;

public interface ConversationMemberClient {

  List<Long> getMemberUserIds(long conversationId);
}
