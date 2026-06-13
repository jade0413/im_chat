package com.im.message.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.im.common.id.SnowflakeIdGenerator;
import com.im.common.mybatis.MybatisPlusConfig;
import com.im.common.mybatis.TenantLineHandlerConfig;
import com.im.common.outbox.OutboxWriter;
import com.im.common.tenant.TenantContext;
import com.im.common.test.IntegrationTestSupport;
import com.im.message.dao.entity.MessageEntity;
import com.im.message.dao.entity.OutboxEntity;
import com.im.message.dao.mapper.ConversationProgressMapper;
import com.im.message.dao.mapper.MessageMapper;
import com.im.message.dao.mapper.OutboxMapper;
import com.im.message.service.ConversationResolver;
import com.im.message.service.MessageAssembler;
import com.im.message.service.MessageIdempotencyService;
import com.im.message.service.MessageFileReferenceValidator;
import com.im.message.service.MessagePersistService;
import com.im.message.service.MessageSendResult;
import com.im.message.service.MessageSendService;
import com.im.message.service.MsgSavedEventFactory;
import com.im.message.service.SequenceService;
import com.im.proto.body.ConvInfo;
import com.im.proto.body.MsgSend;
import com.im.proto.common.ConvType;
import com.im.proto.common.MsgContent;
import com.im.proto.common.TextContent;
import com.im.proto.rpc.ConnCtx;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = {
    MybatisPlusConfig.class,
    TenantLineHandlerConfig.class,
    RedisAutoConfiguration.class,
    SnowflakeIdGenerator.class,
    MessageAssembler.class,
    MessageIdempotencyService.class,
    MessageFileReferenceValidator.class,
    SequenceService.class,
    OutboxWriter.class,
    MsgSavedEventFactory.class,
    MessagePersistService.class,
    MessageSendService.class,
    ConversationProgressMapper.class,
    MessageSendServiceIntegrationTest.TestConfig.class
})
@Testcontainers(disabledWithoutDocker = true)
class MessageSendServiceIntegrationTest extends IntegrationTestSupport {

  @Container
  static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
      .withExposedPorts(6379);

  @DynamicPropertySource
  static void registerRedisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Autowired
  private MessageSendService messageSendService;

  @Autowired
  private ConversationSeedMapper conversationSeedMapper;

  @Autowired
  private MessageMapper messageMapper;

  @Autowired
  private OutboxMapper outboxMapper;

  @Test
  void concurrentTextSendKeepsSeqContinuousAndWritesOutbox() throws Exception {
    long conversationId = nextId();
    TenantContext.runWithTenant(1L,
        () -> conversationSeedMapper.insertConversation(conversationId, "100_200_" + conversationId));

    int workers = 20;
    ExecutorService executor = Executors.newFixedThreadPool(workers);
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<MessageSendResult>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < workers; i++) {
        futures.add(executor.submit(sendTask(ready, start, conversationId, i)));
      }
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      Set<Long> seqs = futures.stream()
          .map(this::getFuture)
          .map(MessageSendResult::seq)
          .collect(Collectors.toSet());
      assertThat(seqs).containsExactlyInAnyOrderElementsOf(
          LongStream.rangeClosed(1, workers).boxed().toList());

      TenantContext.runWithTenant(1L, () -> {
        List<MessageEntity> messages = messageMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers
            .lambdaQuery(MessageEntity.class)
            .eq(MessageEntity::getConversationId, conversationId));
        assertThat(messages).hasSize(workers);
        assertThat(messages).extracting(MessageEntity::getSeq)
            .containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, workers).boxed().toList());

        List<OutboxEntity> outboxes = outboxMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers
            .lambdaQuery(OutboxEntity.class)
            .eq(OutboxEntity::getEventType, MessageAssembler.EVENT_MSG_SAVED));
        assertThat(outboxes).hasSizeGreaterThanOrEqualTo(workers);
        assertThat(conversationSeedMapper.selectMaxSeq(conversationId)).isEqualTo((long) workers);
      });
    } finally {
      executor.shutdownNow();
    }
  }

  private Callable<MessageSendResult> sendTask(CountDownLatch ready, CountDownLatch start,
      long conversationId, int index) {
    return () -> {
      ready.countDown();
      assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
      return TenantContext.callWithTenant(1L,
          () -> messageSendService.send(ctx(), request(conversationId, "client-" + conversationId + "-" + index)));
    };
  }

  private MessageSendResult getFuture(Future<MessageSendResult> future) {
    try {
      return future.get(10, TimeUnit.SECONDS);
    } catch (Exception ex) {
      throw new AssertionError(ex);
    }
  }

  private ConnCtx ctx() {
    return ConnCtx.newBuilder()
        .setTenantId(1L)
        .setUserId(100L)
        .build();
  }

  private MsgSend request(long conversationId, String clientMsgId) {
    return MsgSend.newBuilder()
        .setClientMsgId(clientMsgId)
        .setConvId(conversationId)
        .setContent(MsgContent.newBuilder()
            .setText(TextContent.newBuilder().setText("hello " + clientMsgId)))
        .build();
  }

  private long nextId() {
    return 40_000_000_000L + System.nanoTime();
  }

  @Configuration
  static class TestConfig {

    @Bean
    ConversationResolver conversationResolver() {
      return (ctx, request) -> ConvInfo.newBuilder()
          .setConvId(request.getConvId())
          .setType(ConvType.C2C)
          .setPeerUserId(200L)
          .build();
    }
  }

}
