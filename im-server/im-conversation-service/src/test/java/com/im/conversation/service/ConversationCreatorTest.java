package com.im.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.im.common.id.SnowflakeIdGenerator;
import com.im.conversation.dao.entity.ConversationEntity;
import com.im.conversation.dao.entity.ConversationMemberEntity;
import com.im.conversation.dao.mapper.ConversationMapper;
import com.im.conversation.dao.mapper.ConversationMemberMapper;
import com.im.proto.common.ConvType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationCreatorTest {

  @Mock
  private ConversationMapper conversationMapper;

  @Mock
  private ConversationMemberMapper memberMapper;

  @Mock
  private SnowflakeIdGenerator idGenerator;

  @Captor
  private ArgumentCaptor<ConversationEntity> conversationCaptor;

  @Captor
  private ArgumentCaptor<ConversationMemberEntity> memberCaptor;

  @Test
  void createsConversationAndBothMembers() {
    when(idGenerator.nextId()).thenReturn(9001L);
    when(conversationMapper.insert(any(ConversationEntity.class))).thenReturn(1);
    when(memberMapper.insert(any(ConversationMemberEntity.class))).thenReturn(1);

    ConversationCreator creator = new ConversationCreator(conversationMapper, memberMapper, idGenerator);
    ConversationEntity created = creator.createC2c("100_200", 100L, 200L);

    assertThat(created.getId()).isEqualTo(9001L);
    org.mockito.Mockito.verify(conversationMapper).insert(conversationCaptor.capture());
    assertThat(conversationCaptor.getValue().getType()).isEqualTo(ConvType.C2C.getNumber());
    assertThat(conversationCaptor.getValue().getC2cKey()).isEqualTo("100_200");
    assertThat(conversationCaptor.getValue().getMaxSeq()).isZero();
    assertThat(conversationCaptor.getValue().getLastMsgAbstract()).isEmpty();

    org.mockito.Mockito.verify(memberMapper, org.mockito.Mockito.times(2)).insert(memberCaptor.capture());
    List<ConversationMemberEntity> members = memberCaptor.getAllValues();
    assertThat(members).extracting(ConversationMemberEntity::getConvId)
        .containsExactly(9001L, 9001L);
    assertThat(members).extracting(ConversationMemberEntity::getUserId)
        .containsExactly(100L, 200L);
    assertThat(members).extracting(ConversationMemberEntity::getReadSeq)
        .containsExactly(0L, 0L);
  }
}
