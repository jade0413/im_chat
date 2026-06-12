package com.im.bootstrap.grpc;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.SmartLifecycle;

@Configuration
public class GrpcServerConfig {

  @Bean
  public SmartLifecycle grpcServerLifecycle(List<BindableService> services,
      List<ServerInterceptor> interceptors,
      @Value("${im.grpc.port:9091}") int port) {
    return new GrpcServerLifecycle(services, interceptors, port);
  }

  private static final class GrpcServerLifecycle implements SmartLifecycle {

    private final List<BindableService> services;
    private final List<ServerInterceptor> interceptors;
    private final int port;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private Server server;
    private boolean running;

    private GrpcServerLifecycle(List<BindableService> services,
        List<ServerInterceptor> interceptors,
        int port) {
      this.services = services;
      this.interceptors = interceptors;
      this.port = port;
    }

    @Override
    public void start() {
      NettyServerBuilder builder = NettyServerBuilder.forPort(port)
          .executor(executor);
      services.forEach(service -> builder.addService(ServerInterceptors.intercept(service, interceptors)));
      try {
        server = builder.build().start();
        running = true;
      } catch (IOException ex) {
        throw new ImException(ErrorCode.INTERNAL_ERROR, "failed to start gRPC server");
      }
    }

    @Override
    public void stop() {
      if (server != null) {
        server.shutdown();
      }
      executor.shutdown();
      running = false;
    }

    @Override
    public boolean isRunning() {
      return running;
    }
  }
}
