package com.im.push.service;

import com.im.common.mq.RabbitMqPublisher;
import com.im.push.config.PushProperties;
import com.im.proto.rpc.PushEnvelope;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class GatewayPushPublisher {

  private static final String HEADER_TENANT_ID = "tenant_id";
  private static final String HEADER_TRACE_ID = "trace_id";
  private static final String HEADER_CMD = "cmd";

  private final RabbitTemplate rabbitTemplate;
  private final PushProperties properties;

  public GatewayPushPublisher(RabbitTemplate rabbitTemplate, PushProperties properties) {
    this.rabbitTemplate = rabbitTemplate;
    this.properties = properties;
  }

  public void publish(String gwInstance, PushEnvelope envelope) {
    String queueName = properties.gatewayQueuePrefix() + gwInstance;
    rabbitTemplate.convertAndSend("", queueName, envelope.toByteArray(), message -> {
      MessageProperties messageProperties = message.getMessageProperties();
      messageProperties.setContentType(MessageProperties.CONTENT_TYPE_BYTES);
      messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
      messageProperties.setHeader(HEADER_TENANT_ID, envelope.getTenantId());
      messageProperties.setHeader(HEADER_TRACE_ID, envelope.getTraceId());
      messageProperties.setHeader(HEADER_CMD, envelope.getCmd());
      messageProperties.setHeader(RabbitMqPublisher.HEADER_EVENT_TYPE, "push.envelope");
      return message;
    });
  }
}
