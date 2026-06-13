package com.im.conversation.service;

import com.im.proto.body.ConvInfo;
import java.util.List;

public record ConversationListResult(List<ConvInfo> convs, boolean hasMore, long convListVersion) {
}
