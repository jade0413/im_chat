package com.im.user.config;

import com.im.proto.rpc.MessageRpcGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * user 模块出站 gRPC stub（in-process channel，MVP 单进程指向 localhost，D5）。
 * 目前仅用于好友系统通知（D40，T38）调 message 模块 SendSystemNotification。
 */
@Configuration
public class UserGrpcClientConfig {

  @Bean(name = "userMessageRpcChannel", destroyMethod = "shutdownNow")
  public ManagedChannel userMessageRpcChannel(
      @Value("${im.rpc.message.host:localhost}") String host,
      @Value("${im.rpc.message.port:${im.grpc.port:9091}}") int port) {
    return ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
  }

  @Bean
  public MessageRpcGrpc.MessageRpcBlockingStub userMessageRpcBlockingStub(
      @Qualifier("userMessageRpcChannel") ManagedChannel channel) {
    return MessageRpcGrpc.newBlockingStub(channel);
  }
}
