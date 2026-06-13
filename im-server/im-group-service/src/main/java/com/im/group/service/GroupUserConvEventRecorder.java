package com.im.group.service;

import com.im.common.conversation.UserConvEventType;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.group.dao.entity.GroupUserConvEventEntity;
import com.im.group.dao.mapper.GroupUserConvEventMapper;
import com.im.group.dao.mapper.GroupUserConvVersionMapper;
import org.springframework.stereotype.Service;

@Service
public class GroupUserConvEventRecorder {

  private final GroupUserConvVersionMapper versionMapper;
  private final GroupUserConvEventMapper eventMapper;

  public GroupUserConvEventRecorder(GroupUserConvVersionMapper versionMapper,
      GroupUserConvEventMapper eventMapper) {
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
    GroupUserConvEventEntity event = new GroupUserConvEventEntity();
    event.setTenantId(tenantId);
    event.setUserId(userId);
    event.setConvId(conversationId);
    event.setEventVersion(version);
    event.setEventType(eventType.value());
    eventMapper.insert(event);
    return version;
  }
}
