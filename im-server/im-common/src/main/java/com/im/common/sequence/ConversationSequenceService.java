package com.im.common.sequence;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.sequence.dao.mapper.ConversationSequenceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会话消息 seq 唯一分配入口。
 *
 * <p>D26 契约：seq 分配必须和 message/conversation/outbox 写入处于同一个数据库事务中，
 * 依赖 MySQL 行锁持有到外层事务提交，保证回滚不留下 seq 空洞。
 */
@Service
public class ConversationSequenceService {

  private final ConversationSequenceMapper sequenceMapper;

  public ConversationSequenceService(ConversationSequenceMapper sequenceMapper) {
    this.sequenceMapper = sequenceMapper;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public long nextSeq(long conversationId) {
    if (conversationId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "conversationId must be positive");
    }
    int updated = sequenceMapper.incrementMaxSeq(conversationId);
    if (updated != 1) {
      throw new ImException(ErrorCode.CONV_NOT_FOUND, "conversation not found when allocating seq");
    }
    Long seq = sequenceMapper.selectMaxSeq(conversationId);
    if (seq == null || seq <= 0) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "failed to allocate conversation seq");
    }
    return seq;
  }

  public long currentSeq(long conversationId) {
    if (conversationId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "conversationId must be positive");
    }
    Long seq = sequenceMapper.selectMaxSeq(conversationId);
    if (seq == null) {
      throw new ImException(ErrorCode.CONV_NOT_FOUND);
    }
    return seq;
  }
}
