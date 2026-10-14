package com.kramp.aggregator.api.grpc;

import com.kramp.aggregator.api.grpc.v1.GetProductRequest;
import com.kramp.aggregator.api.grpc.v1.ProductAggregatorGrpc;
import com.kramp.aggregator.api.grpc.v1.ProductView;
import com.kramp.aggregator.observability.CorrelationId;
import com.kramp.aggregator.presentation.ErrorCode;
import com.kramp.aggregator.presentation.ProductViewException;
import com.kramp.aggregator.presentation.ProductViewService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.MDC;
import org.springframework.grpc.server.service.GrpcService;

/** gRPC `GetProduct`: the same service and the same view as REST, with gRPC status codes. */
@GrpcService
final class ProductGrpcService extends ProductAggregatorGrpc.ProductAggregatorImplBase {

  private final ProductViewService views;

  ProductGrpcService(ProductViewService views) {
    this.views = views;
  }

  @Override
  public void getProduct(GetProductRequest request, StreamObserver<ProductView> responses) {
    MDC.put(CorrelationId.MDC_KEY, CorrelationIdInterceptor.CORRELATION_ID.get());
    try {
      responses.onNext(
          ProductViewMapper.toProto(
              views.view(
                  absentIfEmpty(request.getProductId()),
                  absentIfEmpty(request.getMarket()),
                  request.hasCustomerId() ? request.getCustomerId() : null)));
      responses.onCompleted();
    } catch (ProductViewException failure) {
      responses.onError(
          status(failure.code()).withDescription(description(failure)).asRuntimeException());
    } finally {
      MDC.remove(CorrelationId.MDC_KEY);
    }
  }

  /** proto3 cannot tell an empty string from a field the client did not send. */
  private static String absentIfEmpty(String value) {
    return value.isEmpty() ? null : value;
  }

  private static Status status(ErrorCode code) {
    return switch (code) {
      case INVALID_REQUEST, UNSUPPORTED_MARKET -> Status.INVALID_ARGUMENT;
      case PRODUCT_NOT_FOUND, PRODUCT_NOT_IN_MARKET -> Status.NOT_FOUND;
      case CATALOG_UNAVAILABLE -> Status.UNAVAILABLE;
      case CATALOG_TIMEOUT -> Status.DEADLINE_EXCEEDED;
    };
  }

  /** REST sends the supported markets as a JSON field; gRPC has only the description for them. */
  private static String description(ProductViewException failure) {
    return failure.supportedMarkets().isEmpty()
        ? failure.detail()
        : failure.detail() + " (" + String.join(", ", failure.supportedMarkets()) + ")";
  }
}
