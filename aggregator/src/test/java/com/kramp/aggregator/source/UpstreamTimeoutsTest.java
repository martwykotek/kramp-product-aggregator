package com.kramp.aggregator.source;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.kramp.aggregator.aggregation.ProductDataSource;
import com.kramp.aggregator.aggregation.ProductRequest;
import com.kramp.aggregator.aggregation.UpstreamTimeoutException;
import com.kramp.aggregator.source.availability.AvailabilitySource;
import com.kramp.aggregator.source.catalog.CatalogSource;
import com.kramp.aggregator.source.customer.CustomerSource;
import com.kramp.aggregator.source.pricing.PricingSource;
import com.kramp.aggregator.testing.TestProperties;
import com.kramp.aggregator.testing.Timing;
import com.kramp.aggregator.testing.UpstreamClients;
import com.kramp.aggregator.testing.UpstreamStubs;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UpstreamTimeoutsTest {

  /** A timeout may fire this much before the budget: timer granularity, not a broken budget. */
  static final Duration EARLY_TOLERANCE = Duration.ofMillis(20);

  @RegisterExtension
  static WireMockExtension upstream =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static final ProductRequest REQUEST =
      new ProductRequest("W6103", TestProperties.market("pl-PL"), Optional.of("C-1002"));

  static ProductDataSource<?> source(String name) {
    var client = UpstreamClients.client(upstream.baseUrl(), name);
    return switch (name) {
      case "catalog" -> new CatalogSource(client);
      case "pricing" -> new PricingSource(client);
      case "availability" -> new AvailabilitySource(client);
      case "customer" -> new CustomerSource(client);
      default -> throw new IllegalArgumentException("no source named " + name);
    };
  }

  static Stream<Arguments> sources() {
    return Stream.of(
        arguments("catalog", UpstreamStubs.CATALOG, 250),
        arguments("pricing", UpstreamStubs.PRICING, 300),
        arguments("availability", UpstreamStubs.AVAILABILITY, 350),
        arguments("customer", UpstreamStubs.CUSTOMERS, 200));
  }

  static void assertEndedWithinBudget(Throwable thrown, Duration elapsed, Duration budget) {
    assertThat(thrown).isInstanceOf(UpstreamTimeoutException.class);
    assertThat(elapsed)
        .isGreaterThanOrEqualTo(budget.minus(EARLY_TOLERANCE))
        .isLessThanOrEqualTo(budget.plus(Timing.SCHEDULING_TOLERANCE));
  }

  @Nested
  class Hanging {

    // proves: REQ-AGG-RES-010
    @ParameterizedTest(name = "{0}")
    @MethodSource("com.kramp.aggregator.source.UpstreamTimeoutsTest#sources")
    void shouldEndCallWithinBudgetWhenUpstreamHangs(String name, String path, int budgetMillis) {
      // Given
      upstream.stubFor(UpstreamStubs.slow(path, 1000));
      ProductDataSource<?> source = source(name);
      assertThat(TestProperties.policy(name).timeout()).hasMillis(budgetMillis);

      // When
      long start = System.nanoTime();
      Throwable thrown = catchThrowable(() -> source.fetch(REQUEST));
      Duration elapsed = Timing.since(start);

      // Then
      assertEndedWithinBudget(thrown, elapsed, Duration.ofMillis(budgetMillis));
    }

    @Test
    void shouldEndCallWithinBudgetWhenBodyTrickles() {
      // Given — headers at once, then the body in 5 chunks over a second
      upstream.stubFor(
          get(urlPathMatching(UpstreamStubs.CATALOG))
              .willReturn(
                  okJson(UpstreamStubs.catalogBody("W6103", "pl"))
                      .withChunkedDribbleDelay(5, 1000)));
      ProductDataSource<?> source = source("catalog");

      // When
      long start = System.nanoTime();
      Throwable thrown = catchThrowable(() -> source.fetch(REQUEST));
      Duration elapsed = Timing.since(start);

      // Then
      assertEndedWithinBudget(thrown, elapsed, TestProperties.policy("catalog").timeout());
    }
  }
}
