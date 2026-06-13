package com.im.push.config;

import com.im.proto.rpc.ConversationRpcGrpc;
import com.im.proto.rpc.UserRpcGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PushRpcClientConfig {

  // --- ConversationRpc channel ---

  @Bean(name = "pushConversationRpcChannel", destroyMethod = "shutdownNow")
  public ManagedChannel pushConversationRpcChannel(
      @Value("${im.rpc.conversation.host:localhost}") String host,
      @Value("${im.rpc.conversation.port:${im.grpc.port:9091}}") int port) {
    return ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
  }

  @Bean
  public ConversationRpcGrpc.ConversationRpcBlockingStub pushConversationRpcBlockingStub(
      @Qualifier("pushConversationRpcChannel") ManagedChannel channel) {
    return ConversationRpcGrpc.newBlockingStub(channel);
  }

  // --- UserRpc channel（D33: CS open 会话推送时查在线坐席列表）---

  @Bean(name = "pushUserRpcChannel", destroyMethod = "shutdownNow")
  public ManagedChannel pushUserRpcChannel(
      @Value("${im.rpc.user.host:localhost}") String host,
      @Value("${im.rpc.user.port:${im.grpc.port:9091}}") int port) {
    return ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
  }

  @Bean
  public UserRpcGrpc.UserRpcBlockingStub pushUserRpcBlockingStub(
      @Qualifier("pushUserRpcChannel") ManagedChannel channel) {
    return UserRpcGrpc.newBlockingStub(channel);
  }
}
