package com.im.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.im.common.conversation.UserConvEventType;
import com.im.conversation.dao.entity.UserConvEventEntity;
import com.im.conversation.dao.mapper.ConversationUserConvEventMapper;
import com.im.conversation.dao.mapper.ConversationUserConvVersionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserConvEventRecorderTest {

  @Mock
  private ConversationUserConvVersionMapper versionMapper;

  @Mock
  private ConversationUserConvEventMapper eventMapper;

  @Captor
  private ArgumentCaptor<UserConvEventEntity> eventCaptor;

  @Test
  void incrementsVersionAndWritesEvent() {
    when(versionMapper.increment(1L, 100L)).thenReturn(1);
    when(versionMapper.selectVersion(1L, 100L)).thenReturn(7L);

    long version = new UserConvEventRecorder(versionMapper, eventMapper)
        .record(1L, 100L, 501L, UserConvEventType.CREATED);

    assertThat(version).isEqualTo(7L);
    verify(versionMapper).insertInitial(1L, 100L);
    verify(versionMapper).increment(1L, 100L);
    verify(eventMapper).insert(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getTenantId()).isEqualTo(1L);
    assertThat(eventCaptor.getValue().getUserId()).isEqualTo(100L);
    assertThat(eventCaptor.getValue().getConvId()).isEqualTo(501L);
    assertThat(eventCaptor.getValue().getEventVersion()).isEqualTo(7L);
    assertThat(eventCaptor.getValue().getEventType()).isEqualTo("created");
  }
}
