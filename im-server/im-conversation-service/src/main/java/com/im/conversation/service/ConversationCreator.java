package com.im.conversation.service;

import com.im.common.id.SnowflakeIdGenerator;
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

  public ConversationCreator(ConversationMapper conversationMapper,
      ConversationMemberMapper memberMapper,
      SnowflakeIdGenerator idGenerator) {
    this.conversationMapper = conversationMapper;
    this.memberMapper = memberMapper;
    this.idGenerator = idGenerator;
  }

  @Transactional
  public ConversationEntity createC2c(String c2cKey, long fromUserId, long toUserId) {
    ConversationEntity conversation = new ConversationEntity();
    conversation.setId(idGenerator.nextId());
    conversation.setType(ConvType.C2C.getNumber());
    conversation.setC2cKey(c2cKey);
    conversation.setMaxSeq(0L);
    conversation.setLastMsgAbstract("");
    conversationMapper.insert(conversation);

    memberMapper.insert(member(conversation.getId(), fromUserId));
    memberMapper.insert(member(conversation.getId(), toUserId));
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
