package com.im.message.config;

import com.im.proto.rpc.ConversationRpcGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessageRpcClientConfig {

  @Bean(destroyMethod = "shutdownNow")
  public ManagedChannel conversationRpcChannel(
      @Value("${im.rpc.conversation.host:localhost}") String host,
      @Value("${im.rpc.conversation.port:${im.grpc.port:9091}}") int port) {
    return ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
  }

  @Bean
  public ConversationRpcGrpc.ConversationRpcBlockingStub conversationRpcBlockingStub(
      @Qualifier("conversationRpcChannel") ManagedChannel channel) {
    return ConversationRpcGrpc.newBlockingStub(channel);
  }
}
