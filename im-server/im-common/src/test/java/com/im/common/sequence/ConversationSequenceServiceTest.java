package com.im.common.sequence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.sequence.dao.mapper.ConversationSequenceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationSequenceServiceTest {

  @Mock
  private ConversationSequenceMapper sequenceMapper;

  @Test
  void incrementsConversationSeqInDatabase() {
    when(sequenceMapper.incrementMaxSeq(501L)).thenReturn(1);
    when(sequenceMapper.selectAllocatedSeq()).thenReturn(3L);

    long seq = new ConversationSequenceService(sequenceMapper).nextSeq(501L);

    assertThat(seq).isEqualTo(3L);
  }

  @Test
  void returnsCurrentConversationSeq() {
    when(sequenceMapper.selectMaxSeq(501L)).thenReturn(7L);

    long seq = new ConversationSequenceService(sequenceMapper).currentSeq(501L);

    assertThat(seq).isEqualTo(7L);
  }

  @Test
  void rejectsMissingConversationOnAllocate() {
    when(sequenceMapper.incrementMaxSeq(501L)).thenReturn(0);

    assertThatThrownBy(() -> new ConversationSequenceService(sequenceMapper).nextSeq(501L))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CONV_NOT_FOUND);
  }

  @Test
  void rejectsNonPositiveResultAfterAllocate() {
    when(sequenceMapper.incrementMaxSeq(501L)).thenReturn(1);
    when(sequenceMapper.selectAllocatedSeq()).thenReturn(0L);

    assertThatThrownBy(() -> new ConversationSequenceService(sequenceMapper).nextSeq(501L))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INTERNAL_ERROR);
  }
}
