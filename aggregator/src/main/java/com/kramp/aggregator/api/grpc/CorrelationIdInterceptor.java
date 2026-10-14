package com.kramp.aggregator.api.grpc;

import com.kramp.aggregator.observability.CorrelationId;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

/**
 * The gRPC counterpart of the REST correlation filter: accepts the client's `x-correlation-id` when
 * it is safe to repeat, otherwise generates one. The id travels in the gRPC context because the
 * call may continue on another thread than the one that read the metadata.
 */
@Component
@GlobalServerInterceptor
final class CorrelationIdInterceptor implements ServerInterceptor {

  static final Context.Key<String> CORRELATION_ID = Context.key(CorrelationId.MDC_KEY);

  private static final Metadata.Key<String> HEADER =
      Metadata.Key.of(CorrelationId.HEADER, Metadata.ASCII_STRING_MARSHALLER);

  @Override
  public <Q, R> ServerCall.Listener<Q> interceptCall(
      ServerCall<Q, R> call, Metadata headers, ServerCallHandler<Q, R> next) {
    String correlationId = CorrelationId.acceptOrGenerate(headers.get(HEADER));
    return Contexts.interceptCall(
        Context.current().withValue(CORRELATION_ID, correlationId), call, headers, next);
  }
}
