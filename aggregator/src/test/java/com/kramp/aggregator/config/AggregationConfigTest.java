package com.kramp.aggregator.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.kramp.aggregator.presentation.ProductViewService;
import com.kramp.aggregator.testing.FakeSource;
import com.kramp.aggregator.testing.FakeSource.Step;
import com.kramp.aggregator.testing.TestLabels;
import com.kramp.aggregator.testing.TestProperties;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;

class AggregationConfigTest {

  static FakeSource<String> fake(String name) {
    Step<String> answer = Step.value(name, Duration.ofMillis(1));
    return name.equals("catalog")
        ? FakeSource.required(name, answer)
        : FakeSource.optional(name, answer);
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableConfigurationProperties(AggregatorProperties.class)
  static class Properties {}

  @TestConfiguration(proxyBeanMethods = false)
  static class Messages {
    @Bean
    ResourceBundleMessageSource messageSource() {
      return TestLabels.messageSource();
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ConfiguredSources {
    @Bean
    FakeSource<String> catalog() {
      return fake("catalog");
    }

    @Bean
    FakeSource<String> pricing() {
      return fake("pricing");
    }

    @Bean
    FakeSource<String> availability() {
      return fake("availability");
    }

    @Bean
    FakeSource<String> customer() {
      return fake("customer");
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class UnconfiguredSource {
    @Bean
    FakeSource<String> warranty() {
      return fake("warranty");
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class SecondPricing {
    @Bean
    FakeSource<String> legacyPricing() {
      return fake("pricing");
    }
  }

  final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(Properties.class, Messages.class, AggregationConfig.class)
          .withPropertyValues(
              TestProperties.propertyValues(TestProperties.BASE_URL).toArray(String[]::new));

  @Nested
  class Sources {

    @Test
    void shouldStartWhenEveryConfiguredSourceHasABean() {
      // Given / When / Then
      runner
          .withUserConfiguration(ConfiguredSources.class)
          .run(context -> assertThat(context).hasSingleBean(ProductViewService.class));
    }

    @Test
    void shouldRefuseToStartWhenConfiguredSourceHasNoBean() {
      // Given
      ApplicationContextRunner warrantyConfigured =
          runner.withPropertyValues(
              "aggregator.sources.warranty.base-url=http://localhost:8081",
              "aggregator.sources.warranty.timeout=100ms",
              "aggregator.sources.warranty.typical-latency=20ms");

      // When / Then
      warrantyConfigured
          .withUserConfiguration(ConfiguredSources.class)
          .run(
              context ->
                  assertThat(context.getStartupFailure())
                      .rootCause()
                      .isInstanceOf(IllegalStateException.class)
                      .extracting(Throwable::getMessage, InstanceOfAssertFactories.STRING)
                      .contains("aggregator.sources.warranty"));
    }

    @Test
    void shouldRefuseToStartWhenSourceBeanHasNoConfiguration() {
      // Given / When / Then
      runner
          .withUserConfiguration(ConfiguredSources.class, UnconfiguredSource.class)
          .run(
              context ->
                  assertThat(context.getStartupFailure())
                      .rootCause()
                      .isInstanceOf(IllegalStateException.class)
                      .extracting(Throwable::getMessage, InstanceOfAssertFactories.STRING)
                      .contains("aggregator.sources.warranty"));
    }

    @Test
    void shouldRefuseToStartWhenTwoSourcesShareAName() {
      // Given / When / Then
      runner
          .withUserConfiguration(ConfiguredSources.class, SecondPricing.class)
          .run(
              context ->
                  assertThat(context.getStartupFailure())
                      .rootCause()
                      .isInstanceOf(IllegalStateException.class)
                      .extracting(Throwable::getMessage, InstanceOfAssertFactories.STRING)
                      .contains("pricing"));
    }
  }

  @Nested
  class LabelBundles {

    // proves: REQ-AGG-MARKET-006
    @Test
    void shouldRefuseToStartWhenMarketLanguageHasNoLabelBundle() {
      // Given — there is no messages_it.properties
      ApplicationContextRunner italyConfigured =
          runner
              .withUserConfiguration(ConfiguredSources.class)
              .withPropertyValues(
                  "aggregator.markets[8].code=it-IT", "aggregator.markets[8].currency=EUR");
      AtomicReference<Throwable> failure = new AtomicReference<>();

      // When
      italyConfigured.run(context -> failure.set(context.getStartupFailure()));

      // Then
      assertThat(failure.get())
          .isNotNull()
          .rootCause()
          .isExactlyInstanceOf(IllegalStateException.class)
          .hasMessageContaining("market it-IT")
          .hasMessageContaining("language it");
    }
  }
}
