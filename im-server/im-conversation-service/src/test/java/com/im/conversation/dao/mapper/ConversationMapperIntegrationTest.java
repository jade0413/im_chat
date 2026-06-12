package com.im.conversation.dao.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.im.common.mybatis.MybatisPlusConfig;
import com.im.common.mybatis.TenantLineHandlerConfig;
import com.im.common.tenant.TenantContext;
import com.im.common.test.IntegrationTestSupport;
import com.im.conversation.dao.entity.ConversationEntity;
import com.im.conversation.dao.entity.ConversationMemberEntity;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = {MybatisPlusConfig.class, TenantLineHandlerConfig.class})
@Testcontainers(disabledWithoutDocker = true)
class ConversationMapperIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private ConversationMapper conversationMapper;

  @Autowired
  private ConversationMemberMapper memberMapper;

  @Test
  void insertsUpdatesAndSelectsConversationWithinTenant() {
    long conversationId = nextId();

    TenantContext.runWithTenant(1L, () -> {
      ConversationEntity conversation = new ConversationEntity();
      conversation.setId(conversationId);
      conversation.setType(1);
      conversation.setC2cKey("100_200_" + conversationId);
      conversation.setMaxSeq(0L);
      conversation.setLastMsgAbstract("");

      assertThat(conversationMapper.insert(conversation)).isEqualTo(1);

      ConversationEntity patch = new ConversationEntity();
      patch.setId(conversationId);
      patch.setMaxSeq(3L);
      patch.setLastMsgAbstract("hello");
      patch.setLastMsgTime(LocalDateTime.now());
      assertThat(conversationMapper.updateById(patch)).isEqualTo(1);

      ConversationEntity selected = conversationMapper.selectById(conversationId);
      assertThat(selected).isNotNull();
      assertThat(selected.getTenantId()).isEqualTo(1L);
      assertThat(selected.getMaxSeq()).isEqualTo(3L);
      assertThat(selected.getLastMsgAbstract()).isEqualTo("hello");
    });

    TenantContext.runWithTenant(2L,
        () -> assertThat(conversationMapper.selectById(conversationId)).isNull());
  }

  @Test
  void insertsAndSelectsConversationMemberWithinTenant() {
    long conversationId = nextId();
    long userId = nextId();

    TenantContext.runWithTenant(1L, () -> {
      ConversationMemberEntity member = new ConversationMemberEntity();
      member.setConvId(conversationId);
      member.setUserId(userId);
      member.setReadSeq(0L);
      member.setPinned(0);
      member.setMuted(0);

      assertThat(memberMapper.insert(member)).isEqualTo(1);

      ConversationMemberEntity selected = memberMapper.selectOne(Wrappers
          .lambdaQuery(ConversationMemberEntity.class)
          .eq(ConversationMemberEntity::getConvId, conversationId)
          .eq(ConversationMemberEntity::getUserId, userId));
      assertThat(selected).isNotNull();
      assertThat(selected.getTenantId()).isEqualTo(1L);
      assertThat(selected.getReadSeq()).isZero();
    });

    TenantContext.runWithTenant(2L, () -> assertThat(memberMapper.selectList(Wrappers
        .lambdaQuery(ConversationMemberEntity.class)
        .eq(ConversationMemberEntity::getConvId, conversationId)
        .eq(ConversationMemberEntity::getUserId, userId))).isEmpty());
  }

  private long nextId() {
    return 20_000_000_000L + System.nanoTime();
  }
}
