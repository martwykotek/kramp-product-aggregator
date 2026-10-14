package com.kramp.aggregator.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Time budgets, circuit-breaker settings, upstream locations and markets. Validated on binding: the
 * application refuses to start with a budget it could not honour.
 */
@ConfigurationProperties("aggregator")
public record AggregatorProperties(
    Duration globalDeadline,
    CircuitBreakerSettings circuitBreaker,
    Map<String, SourceSettings> sources,
    List<MarketSettings> markets) {

  private static final Pattern MARKET_CODE = Pattern.compile("[a-z]{2}-[A-Z]{2}");

  public AggregatorProperties {
    require(
        globalDeadline != null && globalDeadline.isPositive(),
        "aggregator.global-deadline must be positive, was " + globalDeadline);
    require(circuitBreaker != null, "aggregator.circuit-breaker is required");
    require(
        sources != null && !sources.isEmpty(),
        "aggregator.sources must configure at least one source");
    sources.forEach(
        (name, source) -> source.validate("aggregator.sources." + name, globalDeadline));
    require(
        markets != null && !markets.isEmpty(), "aggregator.markets must list at least one market");
    for (int i = 0; i < markets.size(); i++) {
      markets.get(i).validate("aggregator.markets[" + i + "]");
    }
  }

  /** Where an upstream lives and how long one call to it may take in total (all attempts). */
  public record SourceSettings(URI baseUrl, Duration timeout, Duration typicalLatency) {

    void validate(String prefix, Duration globalDeadline) {
      require(
          baseUrl != null && baseUrl.isAbsolute(),
          prefix + ".base-url must be an absolute URL, was " + baseUrl);
      require(
          timeout != null && timeout.isPositive() && timeout.compareTo(globalDeadline) <= 0,
          prefix
              + ".timeout must be positive and at most aggregator.global-deadline ("
              + globalDeadline.toMillis()
              + " ms), was "
              + timeout);
      require(
          typicalLatency != null
              && typicalLatency.isPositive()
              && typicalLatency.compareTo(timeout) < 0,
          prefix
              + ".typical-latency must be positive and below "
              + prefix
              + ".timeout, was "
              + typicalLatency);
    }
  }

  /** One breaker per source, all with these settings. */
  public record CircuitBreakerSettings(
      int slidingWindowSize,
      int minimumNumberOfCalls,
      float failureRateThreshold,
      Duration waitInOpenState,
      int permittedCallsInHalfOpenState) {

    public CircuitBreakerSettings {
      require(
          slidingWindowSize > 0, "aggregator.circuit-breaker.sliding-window-size must be positive");
      require(
          minimumNumberOfCalls > 0,
          "aggregator.circuit-breaker.minimum-number-of-calls must be positive");
      require(
          failureRateThreshold > 0 && failureRateThreshold <= 100,
          "aggregator.circuit-breaker.failure-rate-threshold must be in (0, 100]");
      require(
          waitInOpenState != null && waitInOpenState.isPositive(),
          "aggregator.circuit-breaker.wait-in-open-state must be positive");
      require(
          permittedCallsInHalfOpenState > 0,
          "aggregator.circuit-breaker.permitted-calls-in-half-open-state must be positive");
    }

    /** The clock drives the open-state wait, so tests can advance it instead of sleeping. */
    public CircuitBreakerConfig toCircuitBreakerConfig(Clock clock) {
      throw new UnsupportedOperationException("not implemented");
    }
  }

  /**
   * Language comes from the code (`pl-PL` → `pl`); currency is the market's price-list currency.
   */
  public record MarketSettings(String code, Currency currency) {

    void validate(String prefix) {
      require(
          code != null && MARKET_CODE.matcher(code).matches(),
          prefix + ".code must look like ll-CC, was " + code);
      // Pseudo-currencies such as XXX ("no currency") have no minor unit and cannot price anything.
      require(
          currency != null && currency.getDefaultFractionDigits() >= 0,
          prefix + ".currency must be an ISO 4217 money currency, was " + currency);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }
}
