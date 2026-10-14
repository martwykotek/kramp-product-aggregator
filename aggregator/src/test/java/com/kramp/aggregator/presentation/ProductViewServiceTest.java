package com.kramp.aggregator.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.kramp.aggregator.aggregation.AggregationListener;
import com.kramp.aggregator.aggregation.GuardedSource;
import com.kramp.aggregator.aggregation.ProductAggregationService;
import com.kramp.aggregator.market.MarketRegistry;
import com.kramp.aggregator.source.availability.StockInfo;
import com.kramp.aggregator.source.catalog.CatalogProduct;
import com.kramp.aggregator.source.customer.CustomerProfile;
import com.kramp.aggregator.source.pricing.PriceQuote;
import com.kramp.aggregator.testing.FakeSource;
import com.kramp.aggregator.testing.FakeSource.Step;
import com.kramp.aggregator.testing.MutableClock;
import com.kramp.aggregator.testing.Products;
import com.kramp.aggregator.testing.TestLabels;
import com.kramp.aggregator.testing.TestProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.slf4j.MDC;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

class ProductViewServiceTest {

  static final Duration OPEN_CIRCUIT_RETRY_AFTER = Duration.ofSeconds(10);

  final MutableClock clock = new MutableClock();
  final CircuitBreaker catalogBreaker =
      CircuitBreaker.of("catalog", TestProperties.breakerConfig(clock));
  final SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("test-upstream-");

  final FakeSource<CatalogProduct> catalog =
      FakeSource.required("catalog", Step.value(Products.W6103_PL, ms(5)));
  final FakeSource<PriceQuote> pricing =
      FakeSource.optional("pricing", Step.value(Products.W6103_PLN_C1002, ms(5)));
  final FakeSource<StockInfo> availability =
      FakeSource.optional("availability", Step.value(Products.W6103_PL_MOD, ms(5)));
  final FakeSource<CustomerProfile> customer =
      FakeSource.optional("customer", Step.value(Products.C1002, ms(5)))
          .applicableWhen(request -> request.customerId().isPresent());

  ProductViewServiceTest() {
    executor.setVirtualThreads(true);
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  static Duration ms(long millis) {
    return Duration.ofMillis(millis);
  }

  ProductViewService service() {
    List<GuardedSource<?>> sources =
        List.of(
            new GuardedSource<>(
                catalog, TestProperties.policy("catalog"), catalogBreaker, System::nanoTime),
            guard(pricing),
            guard(availability),
            guard(customer));
    ProductAggregationService aggregation =
        new ProductAggregationService(
            sources, executor, Duration.ofMillis(500), AggregationListener.NONE);
    return new ProductViewService(
        new MarketRegistry(TestProperties.markets()),
        aggregation,
        new ProductViewAssembler(TestLabels.labels()),
        OPEN_CIRCUIT_RETRY_AFTER);
  }

  private <T> GuardedSource<T> guard(FakeSource<T> fake) {
    return new GuardedSource<>(
        fake,
        TestProperties.policy(fake.name()),
        CircuitBreaker.of(fake.name(), TestProperties.breakerConfig(clock)),
        System::nanoTime);
  }

  int upstreamInvocations() {
    return catalog.invocations()
        + pricing.invocations()
        + availability.invocations()
        + customer.invocations();
  }

  @Nested
  class View {

    @Test
    void shouldPassNormalisedMarketAndCustomerWhenRequestValid() {
      // Given
      ProductViewService service = service();

      // When
      ProductView view = service.view("W6103", "PL_pl", "C-1002");

      // Then
      assertThat(view.market()).isEqualTo("pl-PL");
      assertThat(view.personalization().status()).isEqualTo("PERSONALIZED");
      assertThat(customer.invocations()).isEqualTo(1);
    }

    @Test
    void shouldReportCorrelationIdWhenOneIsInContext() {
      // Given
      ProductViewService service = service();
      MDC.put("correlationId", "it-corr-1");

      // When
      ProductView view = service.view("W6103", "pl-PL", null);

      // Then
      assertThat(view.meta().correlationId()).isEqualTo("it-corr-1");
    }
  }

  @Nested
  class Validation {

    static Stream<String> malformedCustomerIds() {
      return Stream.of("   ", "A".repeat(65), "C-1002&x=1");
    }

    static Stream<String> malformedProductIds() {
      return Stream.of("W6103$", "A".repeat(41), "W 6103");
    }

    // proves: REQ-API-REST-007
    @ParameterizedTest
    @MethodSource("malformedCustomerIds")
    void shouldRejectWhenCustomerIdIsMalformed(String customerId) {
      // Given
      ProductViewService service = service();

      // When
      Throwable thrown = catchThrowable(() -> service.view("W6103", "pl-PL", customerId));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              ProductViewException.class,
              invalid -> assertThat(invalid.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
      assertThat(upstreamInvocations()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("malformedProductIds")
    void shouldRejectWhenProductIdMalformed(String productId) {
      // Given
      ProductViewService service = service();

      // When
      Throwable thrown = catchThrowable(() -> service.view(productId, "pl-PL", null));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              ProductViewException.class,
              invalid -> assertThat(invalid.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
      assertThat(upstreamInvocations()).isZero();
    }

    @Test
    void shouldRejectWhenMarketMissing() {
      // Given
      ProductViewService service = service();

      // When
      Throwable thrown = catchThrowable(() -> service.view("W6103", null, null));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              ProductViewException.class,
              invalid -> assertThat(invalid.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
      assertThat(upstreamInvocations()).isZero();
    }

    @Test
    void shouldRejectWhenMarketUnsupported() {
      // Given
      ProductViewService service = service();

      // When
      Throwable thrown = catchThrowable(() -> service.view("W6103", "xx-XX", null));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              ProductViewException.class,
              unsupported -> {
                assertThat(unsupported.code()).isEqualTo(ErrorCode.UNSUPPORTED_MARKET);
                assertThat(unsupported.supportedMarkets())
                    .containsExactly(
                        "nl-NL", "nl-BE", "fr-BE", "fr-FR", "de-DE", "de-AT", "pl-PL", "en-GB");
              });
      assertThat(upstreamInvocations()).isZero();
    }
  }

  @Nested
  class CatalogFailure {

    static Stream<Arguments> catalogOutcomes() {
      return Stream.of(
          arguments(
              "not in assortment",
              Step.<CatalogProduct>notFound("NOT_IN_ASSORTMENT", ms(5)),
              false,
              ErrorCode.PRODUCT_NOT_IN_MARKET,
              Optional.empty()),
          arguments(
              "unknown product",
              Step.<CatalogProduct>notFound("PRODUCT_NOT_FOUND", ms(5)),
              false,
              ErrorCode.PRODUCT_NOT_FOUND,
              Optional.empty()),
          arguments(
              "timeout",
              Step.<CatalogProduct>timeout(ms(5)),
              false,
              ErrorCode.CATALOG_TIMEOUT,
              Optional.empty()),
          arguments(
              "error",
              Step.<CatalogProduct>unavailableNotRetryable(ms(5)),
              false,
              ErrorCode.CATALOG_UNAVAILABLE,
              Optional.of(Duration.ofSeconds(1))),
          arguments(
              "circuit open",
              Step.value(Products.W6103_PL, ms(5)),
              true,
              ErrorCode.CATALOG_UNAVAILABLE,
              Optional.of(OPEN_CIRCUIT_RETRY_AFTER)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("catalogOutcomes")
    void shouldMapCatalogOutcomeToErrorCodeWhenRequiredSourceFails(
        String label,
        Step<CatalogProduct> step,
        boolean circuitOpen,
        ErrorCode expected,
        Optional<Duration> retryAfter) {
      // Given
      catalog.withSteps(invocation -> step);
      if (circuitOpen) {
        catalogBreaker.transitionToOpenState();
      }
      ProductViewService service = service();

      // When
      Throwable thrown = catchThrowable(() -> service.view("W6103", "pl-PL", null));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              ProductViewException.class,
              failure -> {
                assertThat(failure.code()).isEqualTo(expected);
                assertThat(failure.retryAfter()).isEqualTo(retryAfter);
              });
    }

    @Test
    void shouldNameProductAndMarketWhenProductNotSoldInMarket() {
      // Given
      catalog.withSteps(invocation -> Step.notFound("NOT_IN_ASSORTMENT", ms(5)));
      ProductViewService service = service();

      // When
      Throwable thrown = catchThrowable(() -> service.view("10112PB", "pl-PL", null));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              ProductViewException.class,
              failure ->
                  assertThat(failure.detail())
                      .isEqualTo("Product 10112PB is not sold in market pl-PL"));
    }
  }
}
