package com.im.cs.config;

import com.im.proto.rpc.ConversationRpcGrpc;
import com.im.proto.rpc.UserRpcGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CS 模块使用的 gRPC 阻塞 stub（in-process channel，MVP 单进程下指向 localhost）。
 * 拆分微服务后只需修改 host/port 配置，代码零改动（D5）。
 */
@Configuration
public class CsGrpcClientConfig {

  // --- UserRpc channel ---

  @Bean(name = "csUserRpcChannel", destroyMethod = "shutdownNow")
  public ManagedChannel csUserRpcChannel(
      @Value("${im.rpc.user.host:localhost}") String host,
      @Value("${im.rpc.user.port:${im.grpc.port:9091}}") int port) {
    return ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
  }

  @Bean
  public UserRpcGrpc.UserRpcBlockingStub csUserRpcBlockingStub(
      @Qualifier("csUserRpcChannel") ManagedChannel channel) {
    return UserRpcGrpc.newBlockingStub(channel);
  }

  // --- ConversationRpc channel ---

  @Bean(name = "csConversationRpcChannel", destroyMethod = "shutdownNow")
  public ManagedChannel csConversationRpcChannel(
      @Value("${im.rpc.conversation.host:localhost}") String host,
      @Value("${im.rpc.conversation.port:${im.grpc.port:9091}}") int port) {
    return ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
  }

  @Bean
  public ConversationRpcGrpc.ConversationRpcBlockingStub csConversationRpcBlockingStub(
      @Qualifier("csConversationRpcChannel") ManagedChannel channel) {
    return ConversationRpcGrpc.newBlockingStub(channel);
  }
}
