package com.kramp.aggregator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.kramp.aggregator.testing.TestProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AggregatorPropertiesTest {

  @EnableConfigurationProperties(AggregatorProperties.class)
  static class PropertiesOnly {}

  final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesOnly.class);

  static UnaryOperator<List<String>> set(String key, String value) {
    return values ->
        values.stream()
            .map(entry -> entry.startsWith(key + "=") ? key + "=" + value : entry)
            .toList();
  }

  static UnaryOperator<List<String>> remove(String keyPrefix) {
    return values -> values.stream().filter(entry -> !entry.startsWith(keyPrefix)).toList();
  }

  @Nested
  class Bind {

    static Stream<Arguments> invalidBudgets() {
      return Stream.of(
          arguments(
              "pricing timeout of 0 ms",
              set("aggregator.sources.pricing.timeout", "0ms"),
              "aggregator.sources.pricing.timeout"),
          arguments(
              "pricing timeout above the 500 ms global deadline",
              set("aggregator.sources.pricing.timeout", "600ms"),
              "aggregator.sources.pricing.timeout"),
          arguments(
              "pricing typical latency not below its timeout",
              set("aggregator.sources.pricing.typical-latency", "300ms"),
              "aggregator.sources.pricing.typical-latency"),
          arguments(
              "pricing base URL missing",
              remove("aggregator.sources.pricing.base-url"),
              "aggregator.sources.pricing.base-url"),
          arguments("no markets", remove("aggregator.markets"), "aggregator.markets"),
          arguments(
              "pseudo-currency XXX",
              set("aggregator.markets[0].currency", "XXX"),
              "aggregator.markets[0].currency"));
    }

    // proves: REQ-AGG-RES-018
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBudgets")
    void shouldRefuseToStartWhenBudgetsInvalid(
        String description, UnaryOperator<List<String>> mutation, String offendingProperty) {
      // Given
      String[] values =
          mutation
              .apply(TestProperties.propertyValues(TestProperties.BASE_URL))
              .toArray(String[]::new);
      AtomicReference<Throwable> failure = new AtomicReference<>();

      // When
      runner.withPropertyValues(values).run(context -> failure.set(context.getStartupFailure()));

      // Then
      assertThat(failure.get())
          .isNotNull()
          .rootCause()
          .isExactlyInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(offendingProperty);
    }

    @Test
    void shouldStartWhenBudgetsValid() {
      // Given
      String[] values =
          TestProperties.propertyValues(TestProperties.BASE_URL).toArray(String[]::new);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      AtomicReference<AggregatorProperties> bound = new AtomicReference<>();

      // When
      runner
          .withPropertyValues(values)
          .run(
              context -> {
                failure.set(context.getStartupFailure());
                bound.set(context.getBean(AggregatorProperties.class));
              });

      // Then
      assertThat(failure.get()).isNull();
      assertThat(bound.get().globalDeadline()).isEqualTo(Duration.ofMillis(500));
      assertThat(bound.get().sources().keySet())
          .containsExactly("catalog", "pricing", "availability", "customer");
      assertThat(bound.get().sources().get("pricing").timeout()).isEqualTo(Duration.ofMillis(300));
      assertThat(bound.get().markets()).hasSize(8);
    }
  }
}
