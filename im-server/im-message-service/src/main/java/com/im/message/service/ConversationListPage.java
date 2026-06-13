package com.im.message.service;

import com.im.proto.body.ConvInfo;
import java.util.List;

public record ConversationListPage(List<ConvInfo> convs, boolean hasMore, long convListVersion) {
}
