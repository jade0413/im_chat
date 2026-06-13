package com.im.conversation.service;

import com.im.common.conversation.UserConvEventType;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.conversation.dao.entity.UserConvEventEntity;
import com.im.conversation.dao.mapper.ConversationUserConvEventMapper;
import com.im.conversation.dao.mapper.ConversationUserConvVersionMapper;
import org.springframework.stereotype.Service;

@Service
public class UserConvEventRecorder {

  private final ConversationUserConvVersionMapper versionMapper;
  private final ConversationUserConvEventMapper eventMapper;

  public UserConvEventRecorder(ConversationUserConvVersionMapper versionMapper,
      ConversationUserConvEventMapper eventMapper) {
    this.versionMapper = versionMapper;
    this.eventMapper = eventMapper;
  }

  public long record(long tenantId, long userId, long conversationId, UserConvEventType eventType) {
    versionMapper.insertInitial(tenantId, userId);
    int updated = versionMapper.increment(tenantId, userId);
    if (updated != 1) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "conversation list version update failed");
    }
    Long version = versionMapper.selectVersion(tenantId, userId);
    if (version == null || version <= 0) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "conversation list version not found");
    }
    UserConvEventEntity event = new UserConvEventEntity();
    event.setTenantId(tenantId);
    event.setUserId(userId);
    event.setConvId(conversationId);
    event.setEventVersion(version);
    event.setEventType(eventType.value());
    eventMapper.insert(event);
    return version;
  }
}
