package com.im.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.message.dao.mapper.ConversationProgressMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SequenceServiceTest {

  @Mock
  private ConversationProgressMapper conversationProgressMapper;

  @Test
  void incrementsConversationSeqInDatabase() {
    when(conversationProgressMapper.incrementMaxSeq(501L)).thenReturn(1);
    when(conversationProgressMapper.selectMaxSeq(501L)).thenReturn(3L);

    long seq = new SequenceService(conversationProgressMapper).nextSeq(501L);

    assertThat(seq).isEqualTo(3L);
  }

  @Test
  void rejectsMissingConversation() {
    when(conversationProgressMapper.incrementMaxSeq(501L)).thenReturn(0);

    assertThatThrownBy(() -> new SequenceService(conversationProgressMapper).nextSeq(501L))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CONV_NOT_FOUND);
  }

  @Test
  void rejectsNullDatabaseResult() {
    when(conversationProgressMapper.incrementMaxSeq(501L)).thenReturn(1);
    when(conversationProgressMapper.selectMaxSeq(501L)).thenReturn(null);

    assertThatThrownBy(() -> new SequenceService(conversationProgressMapper).nextSeq(501L))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INTERNAL_ERROR);
  }
}
