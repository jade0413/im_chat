package com.im.message.dao.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.im.common.mybatis.MybatisPlusConfig;
import com.im.common.mybatis.TenantLineHandlerConfig;
import com.im.common.tenant.TenantContext;
import com.im.common.test.IntegrationTestSupport;
import com.im.message.dao.entity.MessageEntity;
import com.im.message.dao.entity.OutboxEntity;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = {MybatisPlusConfig.class, TenantLineHandlerConfig.class})
@Testcontainers(disabledWithoutDocker = true)
class MessageMapperIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private MessageMapper messageMapper;

  @Autowired
  private OutboxMapper outboxMapper;

  @Test
  void insertsUpdatesAndSelectsMessageWithinTenant() {
    long messageId = nextId();
    String clientMsgId = "client_" + messageId;

    TenantContext.runWithTenant(1L, () -> {
      MessageEntity message = new MessageEntity();
      message.setId(messageId);
      message.setConversationId(nextId());
      message.setSeq(1L);
      message.setSenderId(nextId());
      message.setClientMsgId(clientMsgId);
      message.setMsgType(1);
      message.setContent("hello".getBytes(StandardCharsets.UTF_8));
      message.setAbstractText("hello");
      message.setStatus(1);

      assertThat(messageMapper.insert(message)).isEqualTo(1);

      MessageEntity patch = new MessageEntity();
      patch.setId(messageId);
      patch.setStatus(2);
      patch.setRevokeReason(1);
      assertThat(messageMapper.updateById(patch)).isEqualTo(1);

      MessageEntity selected = messageMapper.selectById(messageId);
      assertThat(selected).isNotNull();
      assertThat(selected.getTenantId()).isEqualTo(1L);
      assertThat(selected.getClientMsgId()).isEqualTo(clientMsgId);
      assertThat(selected.getStatus()).isEqualTo(2);
      assertThat(selected.getRevokeReason()).isEqualTo(1);
    });

    TenantContext.runWithTenant(2L, () -> assertThat(messageMapper.selectById(messageId)).isNull());
  }

  @Test
  void insertsAndSelectsOutboxWithinTenant() {
    TenantContext.runWithTenant(1L, () -> {
      OutboxEntity outbox = new OutboxEntity();
      outbox.setEventType("msg.saved");
      outbox.setRoutingKey("tenant.1.conv." + nextId());
      outbox.setPayload("event".getBytes(StandardCharsets.UTF_8));
      outbox.setStatus(0);
      outbox.setRetryCount(0);

      assertThat(outboxMapper.insert(outbox)).isEqualTo(1);
      assertThat(outbox.getId()).isNotNull();

      OutboxEntity selected = outboxMapper.selectById(outbox.getId());
      assertThat(selected).isNotNull();
      assertThat(selected.getTenantId()).isEqualTo(1L);
      assertThat(selected.getEventType()).isEqualTo("msg.saved");
      assertThat(selected.getRetryCount()).isZero();
    });
  }

  private long nextId() {
    return 30_000_000_000L + System.nanoTime();
  }
}
