package com.kramp.aggregator.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.grpc.server.autoconfigure.GrpcServerExecutorProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A gRPC call blocks its thread while the aggregation runs. Without an executor grpc-java serves
 * calls on an unbounded cached pool of platform threads; virtual threads make a blocked call as
 * cheap as it is for REST requests.
 */
@Configuration(proxyBeanMethods = false)
class GrpcServerConfig {

  @Bean
  VirtualThreadExecutorProvider grpcServerExecutorProvider() {
    return new VirtualThreadExecutorProvider(
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("grpc-handler-", 0).factory()));
  }

  /**
   * grpc-java never shuts down an executor it was given; the context closes this one after the gRPC
   * server has stopped, waiting for the calls still running.
   */
  record VirtualThreadExecutorProvider(ExecutorService executor)
      implements GrpcServerExecutorProvider, AutoCloseable {

    @Override
    public Executor getExecutor() {
      return executor;
    }

    @Override
    public void close() {
      executor.close();
    }
  }
}
