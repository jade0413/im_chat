package com.im.conversation.service;

import com.im.common.conversation.UserConvEventType;
import com.im.common.id.SnowflakeIdGenerator;
import com.im.common.tenant.TenantContext;
import com.im.conversation.dao.entity.ConversationEntity;
import com.im.conversation.dao.entity.ConversationMemberEntity;
import com.im.conversation.dao.mapper.ConversationMapper;
import com.im.conversation.dao.mapper.ConversationMemberMapper;
import com.im.proto.common.ConvType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationCreator {

  private final ConversationMapper conversationMapper;
  private final ConversationMemberMapper memberMapper;
  private final SnowflakeIdGenerator idGenerator;
  private final UserConvEventRecorder userConvEventRecorder;

  public ConversationCreator(ConversationMapper conversationMapper,
      ConversationMemberMapper memberMapper,
      SnowflakeIdGenerator idGenerator,
      UserConvEventRecorder userConvEventRecorder) {
    this.conversationMapper = conversationMapper;
    this.memberMapper = memberMapper;
    this.idGenerator = idGenerator;
    this.userConvEventRecorder = userConvEventRecorder;
  }

  @Transactional
  public ConversationEntity createC2c(String c2cKey, long fromUserId, long toUserId) {
    long tenantId = TenantContext.requiredTenantId();
    ConversationEntity conversation = new ConversationEntity();
    conversation.setId(idGenerator.nextId());
    conversation.setType(ConvType.C2C.getNumber());
    conversation.setC2cKey(c2cKey);
    conversation.setMaxSeq(0L);
    conversation.setLastMsgAbstract("");
    conversationMapper.insert(conversation);

    memberMapper.insert(member(conversation.getId(), fromUserId));
    memberMapper.insert(member(conversation.getId(), toUserId));
    userConvEventRecorder.record(tenantId, fromUserId, conversation.getId(), UserConvEventType.CREATED);
    userConvEventRecorder.record(tenantId, toUserId, conversation.getId(), UserConvEventType.CREATED);
    return conversation;
  }

  private ConversationMemberEntity member(long conversationId, long userId) {
    ConversationMemberEntity member = new ConversationMemberEntity();
    member.setConvId(conversationId);
    member.setUserId(userId);
    member.setReadSeq(0L);
    member.setPinned(0);
    member.setMuted(0);
    return member;
  }
}
