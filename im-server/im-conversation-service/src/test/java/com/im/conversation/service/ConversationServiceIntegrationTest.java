package com.im.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.im.common.id.SnowflakeIdGenerator;
import com.im.common.mybatis.MybatisPlusConfig;
import com.im.common.mybatis.TenantLineHandlerConfig;
import com.im.common.tenant.TenantContext;
import com.im.common.test.IntegrationTestSupport;
import com.im.conversation.dao.entity.ConversationEntity;
import com.im.conversation.dao.entity.ConversationMemberEntity;
import com.im.conversation.dao.mapper.ConversationMapper;
import com.im.conversation.dao.mapper.ConversationMemberMapper;
import com.im.proto.body.ConvInfo;
import com.im.proto.rpc.ResolveConvReq;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = {
    MybatisPlusConfig.class,
    TenantLineHandlerConfig.class,
    SnowflakeIdGenerator.class,
    C2cKeyGenerator.class,
    ConversationCreator.class,
    ConversationService.class
})
@Testcontainers(disabledWithoutDocker = true)
class ConversationServiceIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private ConversationService conversationService;

  @Autowired
  private ConversationMapper conversationMapper;

  @Autowired
  private ConversationMemberMapper memberMapper;

  @Test
  void concurrentResolveCreatesSingleC2cConversation() throws Exception {
    long fromUserId = nextId();
    long toUserId = nextId();
    String c2cKey = Math.min(fromUserId, toUserId) + "_" + Math.max(fromUserId, toUserId);
    ResolveConvReq request = ResolveConvReq.newBuilder()
        .setFromUserId(fromUserId)
        .setToUserId(toUserId)
        .build();

    int workers = 8;
    ExecutorService executor = Executors.newFixedThreadPool(workers);
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Long>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < workers; i++) {
        futures.add(executor.submit(resolveTask(ready, start, request)));
      }
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      Set<Long> conversationIds = futures.stream()
          .map(this::getFuture)
          .collect(java.util.stream.Collectors.toSet());
      assertThat(conversationIds).hasSize(1);

      TenantContext.runWithTenant(1L, () -> {
        List<ConversationEntity> conversations = conversationMapper.selectList(Wrappers
            .lambdaQuery(ConversationEntity.class)
            .eq(ConversationEntity::getC2cKey, c2cKey));
        assertThat(conversations).hasSize(1);

        List<ConversationMemberEntity> members = memberMapper.selectList(Wrappers
            .lambdaQuery(ConversationMemberEntity.class)
            .eq(ConversationMemberEntity::getConvId, conversationIds.iterator().next())
            .orderByAsc(ConversationMemberEntity::getUserId));
        assertThat(members).extracting(ConversationMemberEntity::getUserId)
            .containsExactly(Math.min(fromUserId, toUserId), Math.max(fromUserId, toUserId));
      });
    } finally {
      executor.shutdownNow();
    }
  }

  private Callable<Long> resolveTask(CountDownLatch ready, CountDownLatch start, ResolveConvReq request) {
    return () -> {
      ready.countDown();
      assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
      return TenantContext.callWithTenant(1L, () -> {
        ConvInfo conv = conversationService.resolve(request);
        return conv.getConvId();
      });
    };
  }

  private Long getFuture(Future<Long> future) {
    try {
      return future.get(10, TimeUnit.SECONDS);
    } catch (Exception ex) {
      throw new AssertionError(ex);
    }
  }

  private long nextId() {
    return 30_000_000_000L + System.nanoTime();
  }
}
