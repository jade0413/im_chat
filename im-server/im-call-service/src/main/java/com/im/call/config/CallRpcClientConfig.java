package com.im.call.config;

import com.im.proto.rpc.PushRpcGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 跨模块 RPC（D5 铁律）：通话模块推送一律经 push 模块的 PushRpc。 */
@Configuration
@EnableConfigurationProperties(CallProperties.class)
public class CallRpcClientConfig {

  @Bean(destroyMethod = "shutdownNow")
  public ManagedChannel callPushRpcChannel(
      @Value("${im.rpc.push.host:localhost}") String host,
      @Value("${im.rpc.push.port:${im.grpc.port:9091}}") int port) {
    return ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
  }

  @Bean
  public PushRpcGrpc.PushRpcBlockingStub callPushRpcBlockingStub(
      @Qualifier("callPushRpcChannel") ManagedChannel channel) {
    return PushRpcGrpc.newBlockingStub(channel);
  }
}
