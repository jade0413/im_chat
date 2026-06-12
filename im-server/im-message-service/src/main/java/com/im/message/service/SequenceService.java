package com.im.message.service;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.message.dao.mapper.ConversationProgressMapper;
import org.springframework.stereotype.Service;

@Service
public class SequenceService {

  private final ConversationProgressMapper conversationProgressMapper;

  public SequenceService(ConversationProgressMapper conversationProgressMapper) {
    this.conversationProgressMapper = conversationProgressMapper;
  }

  public long nextSeq(long conversationId) {
    if (conversationId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "conversationId must be positive");
    }
    int updated = conversationProgressMapper.incrementMaxSeq(conversationId);
    if (updated != 1) {
      throw new ImException(ErrorCode.CONV_NOT_FOUND, "conversation not found when allocating seq");
    }
    Long seq = conversationProgressMapper.selectMaxSeq(conversationId);
    if (seq == null || seq <= 0) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "failed to allocate conversation seq");
    }
    return seq;
  }
}
