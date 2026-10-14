package com.kramp.aggregator.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kramp.aggregator.aggregation.AggregationResult;
import com.kramp.aggregator.aggregation.FailureKind;
import com.kramp.aggregator.aggregation.ProductRequest;
import com.kramp.aggregator.aggregation.RequiredSourceFailedException;
import com.kramp.aggregator.aggregation.SourceOutcome;
import com.kramp.aggregator.testing.Products;
import com.kramp.aggregator.testing.TestProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

class AggregationTelemetryTest {

  static final ProductRequest ANONYMOUS =
      new ProductRequest("W6103", TestProperties.market("pl-PL"), Optional.empty());
  static final ProductRequest FOR_CUSTOMER =
      new ProductRequest("W6103", TestProperties.market("pl-PL"), Optional.of("C-1002"));

  final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  final AggregationTelemetry telemetry = new AggregationTelemetry(meters);
  final Logger logger = (Logger) LoggerFactory.getLogger(AggregationTelemetry.class);
  final ListAppender<ILoggingEvent> events = new ListAppender<>();

  @BeforeEach
  void captureLog() {
    events.start();
    logger.addAppender(events);
  }

  @AfterEach
  void releaseLog() {
    logger.detachAppender(events);
  }

  /** The AT-OBS-03 result: catalog OK 48 ms, pricing TIMEOUT 300 ms, availability OK 97 ms. */
  static AggregationResult degraded(ProductRequest request) {
    Map<String, SourceOutcome<?>> outcomes = new LinkedHashMap<>();
    outcomes.put("catalog", new SourceOutcome.Ok<>(Products.W6103_PL, Duration.ofMillis(48)));
    outcomes.put(
        "pricing", new SourceOutcome.Failed<>(FailureKind.TIMEOUT, Duration.ofMillis(300)));
    outcomes.put(
        "availability", new SourceOutcome.Ok<>(Products.W6103_PL_MOD, Duration.ofMillis(97)));
    outcomes.put(
        "customer",
        request.customerId().isPresent()
            ? new SourceOutcome.Ok<>(Products.C1002, Duration.ofMillis(61))
            : new SourceOutcome.Skipped<>());
    return new AggregationResult(request, outcomes, true, Duration.ofMillis(312));
  }

  static AggregationResult complete() {
    Map<String, SourceOutcome<?>> outcomes = new LinkedHashMap<>();
    outcomes.put("catalog", new SourceOutcome.Ok<>(Products.W6103_PL, Duration.ofMillis(48)));
    outcomes.put(
        "pricing", new SourceOutcome.Ok<>(Products.W6103_PLN_C1002, Duration.ofMillis(81)));
    outcomes.put(
        "availability", new SourceOutcome.Ok<>(Products.W6103_PL_MOD, Duration.ofMillis(97)));
    outcomes.put("customer", new SourceOutcome.Skipped<>());
    return new AggregationResult(ANONYMOUS, outcomes, false, Duration.ofMillis(99));
  }

  /** Catalog answered 503; the calls still running were cancelled and are reported as TIMEOUT. */
  static RequiredSourceFailedException catalogFailed() {
    SourceOutcome<?> catalog = new SourceOutcome.Failed<>(FailureKind.ERROR, Duration.ofMillis(7));
    Map<String, SourceOutcome<?>> outcomes = new LinkedHashMap<>();
    outcomes.put("catalog", catalog);
    outcomes.put("pricing", new SourceOutcome.Failed<>(FailureKind.TIMEOUT, Duration.ofMillis(8)));
    outcomes.put(
        "availability", new SourceOutcome.Failed<>(FailureKind.TIMEOUT, Duration.ofMillis(8)));
    outcomes.put("customer", new SourceOutcome.Skipped<>());
    return new RequiredSourceFailedException(
        ANONYMOUS, "catalog", catalog, outcomes, Duration.ofMillis(9));
  }

  @Nested
  class SummaryLine {

    static Stream<Arguments> aggregations() {
      return Stream.of(
          arguments(
              "degraded",
              (Consumer<AggregationTelemetry>)
                  telemetry -> telemetry.completed(degraded(ANONYMOUS)),
              "product_aggregated productId=W6103 market=pl-PL customer=absent outcome=DEGRADED"
                  + " durationMs=312 catalog=OK/48 pricing=TIMEOUT/300 availability=OK/97"
                  + " customer=SKIPPED/0"),
          arguments(
              "complete",
              (Consumer<AggregationTelemetry>) telemetry -> telemetry.completed(complete()),
              "product_aggregated productId=W6103 market=pl-PL customer=absent outcome=OK"
                  + " durationMs=99 catalog=OK/48 pricing=OK/81 availability=OK/97"
                  + " customer=SKIPPED/0"),
          arguments(
              "required source failed",
              (Consumer<AggregationTelemetry>) telemetry -> telemetry.failed(catalogFailed()),
              "product_aggregated productId=W6103 market=pl-PL customer=absent outcome=FAILED"
                  + " durationMs=9 catalog=ERROR/7 pricing=TIMEOUT/8 availability=TIMEOUT/8"
                  + " customer=SKIPPED/0"));
    }

    // proves: REQ-OPS-OBS-003
    @ParameterizedTest(name = "{0}")
    @MethodSource("aggregations")
    void shouldLogOneSummaryLineWhenAggregationCompletes(
        String name, Consumer<AggregationTelemetry> aggregation, String expectedLine) {
      // Given / When
      aggregation.accept(telemetry);

      // Then
      assertThat(events.list).hasSize(1);
      assertThat(events.list.getFirst().getLevel()).isEqualTo(Level.INFO);
      assertThat(events.list.getFirst().getFormattedMessage()).isEqualTo(expectedLine);
    }
  }

  @Nested
  class Metrics {

    // proves: REQ-OPS-OBS-004
    @Test
    void shouldRecordMetricsWhenAggregationCompletes() {
      // Given / When
      telemetry.completed(degraded(ANONYMOUS));

      // Then
      assertThat(
              meters
                  .get("upstream.call")
                  .tags("source", "pricing", "outcome", "TIMEOUT")
                  .timer()
                  .count())
          .isEqualTo(1);
      assertThat(
              meters
                  .get("upstream.call")
                  .tags("source", "catalog", "outcome", "OK")
                  .timer()
                  .count())
          .isEqualTo(1);
      assertThat(meters.find("upstream.call").tag("source", "customer").timers()).isEmpty();
      assertThat(meters.get("upstream.call").timers()).hasSize(3);
      assertThat(meters.get("aggregation.result").tag("outcome", "DEGRADED").counter().count())
          .isEqualTo(1.0);
      assertThat(meters.get("aggregation.result").counters()).hasSize(1);
    }

    @Test
    void shouldCountFailureAndTimeCalledSourcesWhenRequiredSourceFails() {
      // Given / When
      telemetry.failed(catalogFailed());

      // Then
      assertThat(meters.get("aggregation.result").tag("outcome", "FAILED").counter().count())
          .isEqualTo(1.0);
      assertThat(
              meters
                  .get("upstream.call")
                  .tags("source", "catalog", "outcome", "ERROR")
                  .timer()
                  .count())
          .isEqualTo(1);
      assertThat(meters.get("upstream.call").timers()).hasSize(3);
    }
  }

  @Nested
  class Privacy {

    // proves: RC-OPS-OBS-001
    @Test
    void shouldNotLogCustomerIdWhenCustomerPresent() {
      // Given
      AggregationResult personalized = degraded(FOR_CUSTOMER);

      // When
      telemetry.completed(personalized);

      // Then
      assertThat(events.list)
          .singleElement()
          .satisfies(
              event -> {
                assertThat(event.getFormattedMessage())
                    .contains("customer=present", "customer=OK/61");
                assertThat(event.getFormattedMessage()).doesNotContain("C-1002");
              });
    }
  }
}
