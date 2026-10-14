package com.kramp.aggregator.testing;

import com.kramp.aggregator.aggregation.SourcePolicy;
import com.kramp.aggregator.config.AggregatorProperties;
import com.kramp.aggregator.market.Market;
import com.kramp.aggregator.market.MarketRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * The configuration from the spec (budgets, breaker, 8 markets) for tests, as properties or bound.
 */
public final class TestProperties {

  public static final String BASE_URL = "http://localhost:8081";

  private TestProperties() {}

  /** `key=value` pairs in configuration order; source order is the order of `meta.sources`. */
  public static List<String> propertyValues(String baseUrl) {
    List<String> values = new ArrayList<>();
    values.add("aggregator.global-deadline=500ms");
    values.add("aggregator.circuit-breaker.sliding-window-size=20");
    values.add("aggregator.circuit-breaker.minimum-number-of-calls=10");
    values.add("aggregator.circuit-breaker.failure-rate-threshold=50");
    values.add("aggregator.circuit-breaker.wait-in-open-state=10s");
    values.add("aggregator.circuit-breaker.permitted-calls-in-half-open-state=3");
    source(values, "catalog", baseUrl, "250ms", "50ms");
    source(values, "pricing", baseUrl, "300ms", "80ms");
    source(values, "availability", baseUrl, "350ms", "100ms");
    source(values, "customer", baseUrl, "200ms", "60ms");
    String[][] markets = {
      {"nl-NL", "EUR"}, {"nl-BE", "EUR"}, {"fr-BE", "EUR"}, {"fr-FR", "EUR"},
      {"de-DE", "EUR"}, {"de-AT", "EUR"}, {"pl-PL", "PLN"}, {"en-GB", "GBP"}
    };
    for (int i = 0; i < markets.length; i++) {
      values.add("aggregator.markets[" + i + "].code=" + markets[i][0]);
      values.add("aggregator.markets[" + i + "].currency=" + markets[i][1]);
    }
    return values;
  }

  /** The same values, bound the way Spring binds `application.yml`. */
  public static AggregatorProperties valid(String baseUrl) {
    Map<String, String> values = new LinkedHashMap<>();
    for (String entry : propertyValues(baseUrl)) {
      int separator = entry.indexOf('=');
      values.put(entry.substring(0, separator), entry.substring(separator + 1));
    }
    return new Binder(new MapConfigurationPropertySource(values))
        .bind("aggregator", AggregatorProperties.class)
        .get();
  }

  public static List<Market> markets() {
    return valid(BASE_URL).markets().stream()
        .map(market -> Market.of(market.code(), market.currency()))
        .toList();
  }

  public static Market market(String code) {
    return new MarketRegistry(markets()).resolve(code);
  }

  public static AggregatorProperties.CircuitBreakerSettings breakerSettings() {
    return valid(BASE_URL).circuitBreaker();
  }

  public static CircuitBreakerConfig breakerConfig(Clock clock) {
    return breakerSettings().toCircuitBreakerConfig(clock);
  }

  /** The spec budget of a configured source; fails for a name the configuration does not have. */
  public static SourcePolicy policy(String source) {
    AggregatorProperties.SourceSettings settings = valid(BASE_URL).sources().get(source);
    if (settings == null) {
      throw new IllegalArgumentException("no source named " + source);
    }
    return new SourcePolicy(settings.timeout(), settings.typicalLatency());
  }

  private static void source(
      List<String> values, String name, String baseUrl, String timeout, String typicalLatency) {
    String prefix = "aggregator.sources." + name;
    values.add(prefix + ".base-url=" + baseUrl);
    values.add(prefix + ".timeout=" + timeout);
    values.add(prefix + ".typical-latency=" + typicalLatency);
  }
}
