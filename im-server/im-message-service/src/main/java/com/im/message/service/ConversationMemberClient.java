package com.im.message.service;

import com.im.proto.body.ConvInfo;
import java.util.List;

public interface ConversationMemberClient {

  List<Long> getMemberUserIds(long conversationId);

  ConvInfo getMemberConv(long userId, long conversationId);

  ConversationListPage listMemberConvs(long userId, long convListVersion);
}
