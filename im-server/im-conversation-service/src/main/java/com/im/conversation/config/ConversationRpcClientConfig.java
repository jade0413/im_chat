package com.im.conversation.config;

import com.im.proto.rpc.PushRpcGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConversationRpcClientConfig {

  @Bean(destroyMethod = "shutdownNow")
  public ManagedChannel conversationPushRpcChannel(
      @Value("${im.rpc.push.host:localhost}") String host,
      @Value("${im.rpc.push.port:${im.grpc.port:9091}}") int port) {
    return ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
  }

  @Bean
  public PushRpcGrpc.PushRpcBlockingStub conversationPushRpcBlockingStub(
      @Qualifier("conversationPushRpcChannel") ManagedChannel channel) {
    return PushRpcGrpc.newBlockingStub(channel);
  }
}
