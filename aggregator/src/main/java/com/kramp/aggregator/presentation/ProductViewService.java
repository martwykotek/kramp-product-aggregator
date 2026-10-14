package com.kramp.aggregator.presentation;

import com.kramp.aggregator.aggregation.ProductAggregationService;
import com.kramp.aggregator.aggregation.ProductRequest;
import com.kramp.aggregator.aggregation.RequiredSourceFailedException;
import com.kramp.aggregator.aggregation.SourceOutcome;
import com.kramp.aggregator.market.Market;
import com.kramp.aggregator.market.MarketRegistry;
import com.kramp.aggregator.market.UnsupportedMarketException;
import com.kramp.aggregator.observability.CorrelationId;
import com.kramp.aggregator.source.catalog.CatalogSource;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The one entry point REST and gRPC share: checks the input before any upstream is called, then
 * aggregates and turns a missing catalog answer into an error code.
 */
public final class ProductViewService {

  private static final Pattern PRODUCT_ID = Pattern.compile("[A-Za-z0-9-]{1,40}");
  private static final Pattern CUSTOMER_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

  /** A failed catalog call may succeed on the next request; an open circuit waits it out. */
  private static final Duration ERROR_RETRY_AFTER = Duration.ofSeconds(1);

  private final MarketRegistry markets;
  private final ProductAggregationService aggregation;
  private final ProductViewAssembler assembler;
  private final Duration openCircuitRetryAfter;

  public ProductViewService(
      MarketRegistry markets,
      ProductAggregationService aggregation,
      ProductViewAssembler assembler,
      Duration openCircuitRetryAfter) {
    this.markets = markets;
    this.aggregation = aggregation;
    this.assembler = assembler;
    this.openCircuitRetryAfter = openCircuitRetryAfter;
  }

  /** A null market or customer id means the client did not send one. */
  public ProductView view(String productId, String marketCode, String customerId) {
    if (productId == null || !PRODUCT_ID.matcher(productId).matches()) {
      throw invalid("productId must be 1 to 40 letters, digits or hyphens");
    }
    if (marketCode == null) {
      throw invalid("market is required");
    }
    Market market = resolve(marketCode);
    if (customerId != null && !CUSTOMER_ID.matcher(customerId).matches()) {
      throw invalid("the customer id must be 1 to 64 letters, digits or hyphens");
    }
    ProductRequest request = new ProductRequest(productId, market, Optional.ofNullable(customerId));
    try {
      return assembler.assemble(aggregation.aggregate(request), CorrelationId.current());
    } catch (RequiredSourceFailedException failure) {
      throw translate(failure);
    }
  }

  private Market resolve(String marketCode) {
    try {
      return markets.resolve(marketCode);
    } catch (UnsupportedMarketException unsupported) {
      throw new ProductViewException(
          ErrorCode.UNSUPPORTED_MARKET,
          "This market is not supported; supportedMarkets lists the markets served",
          unsupported.supported(),
          Optional.empty());
    }
  }

  private ProductViewException translate(RequiredSourceFailedException failure) {
    ProductRequest request = failure.request();
    return switch (failure.outcome()) {
      case SourceOutcome.NotFound<?> notFound
          when CatalogSource.NOT_IN_ASSORTMENT.equals(notFound.reason()) ->
          error(
              ErrorCode.PRODUCT_NOT_IN_MARKET,
              "Product %s is not sold in market %s"
                  .formatted(request.productId(), request.market().code()));
      case SourceOutcome.NotFound<?> notFound ->
          error(
              ErrorCode.PRODUCT_NOT_FOUND,
              "Product %s does not exist".formatted(request.productId()));
      case SourceOutcome.Failed<?> failed ->
          switch (failed.kind()) {
            case TIMEOUT -> error(ErrorCode.CATALOG_TIMEOUT, "The catalog did not answer in time");
            case ERROR -> unavailable(ERROR_RETRY_AFTER);
            case CIRCUIT_OPEN -> unavailable(openCircuitRetryAfter);
          };
      case SourceOutcome.Ok<?> ok ->
          throw new IllegalStateException("a required source that answered cannot fail");
      case SourceOutcome.Skipped<?> skipped ->
          throw new IllegalStateException("a required source that was skipped cannot fail");
    };
  }

  private static ProductViewException unavailable(Duration retryAfter) {
    return new ProductViewException(
        ErrorCode.CATALOG_UNAVAILABLE,
        "The catalog is unavailable",
        List.of(),
        Optional.of(retryAfter));
  }

  private static ProductViewException error(ErrorCode code, String detail) {
    return new ProductViewException(code, detail, List.of(), Optional.empty());
  }

  private static ProductViewException invalid(String detail) {
    return error(ErrorCode.INVALID_REQUEST, detail);
  }
}
