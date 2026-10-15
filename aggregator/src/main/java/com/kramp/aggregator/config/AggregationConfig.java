package com.kramp.aggregator.config;

import com.kramp.aggregator.aggregation.AggregationListener;
import com.kramp.aggregator.aggregation.GuardedSource;
import com.kramp.aggregator.aggregation.ProductAggregationService;
import com.kramp.aggregator.aggregation.ProductDataSource;
import com.kramp.aggregator.aggregation.SourcePolicy;
import com.kramp.aggregator.market.Market;
import com.kramp.aggregator.market.MarketRegistry;
import com.kramp.aggregator.observability.MdcTaskDecorator;
import com.kramp.aggregator.presentation.Labels;
import com.kramp.aggregator.presentation.ProductViewAssembler;
import com.kramp.aggregator.presentation.ProductViewService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/** Builds the aggregation from the configuration: one guarded call per configured source. */
@Configuration(proxyBeanMethods = false)
class AggregationConfig {

  @Bean
  CircuitBreakerRegistry circuitBreakerRegistry(AggregatorProperties properties) {
    return CircuitBreakerRegistry.of(
        properties.circuitBreaker().toCircuitBreakerConfig(Clock.systemUTC()));
  }

  @Bean
  MarketRegistry marketRegistry(AggregatorProperties properties) {
    return new MarketRegistry(
        properties.markets().stream()
            .map(market -> Market.of(market.code(), market.currency()))
            .toList());
  }

  /** One virtual thread per upstream call; the caller's correlation id travels with it. */
  @Bean
  SimpleAsyncTaskExecutor upstreamExecutor() {
    SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("upstream-");
    executor.setVirtualThreads(true);
    executor.setTaskDecorator(new MdcTaskDecorator());
    return executor;
  }

  /**
   * Sources run in configuration order, which is the order of `meta.sources`. A source without
   * configuration, or configuration without a source, is a deployment mistake and stops startup.
   */
  @Bean
  ProductAggregationService productAggregationService(
      AggregatorProperties properties,
      List<ProductDataSource<?>> sources,
      CircuitBreakerRegistry breakers,
      @Qualifier("upstreamExecutor") SimpleAsyncTaskExecutor upstreamExecutor,
      ObjectProvider<AggregationListener> listener) {
    Map<String, ProductDataSource<?>> byName = new LinkedHashMap<>();
    for (ProductDataSource<?> source : sources) {
      if (byName.put(source.name(), source) != null) {
        throw new IllegalStateException("two ProductDataSource beans are named " + source.name());
      }
    }
    List<GuardedSource<?>> guarded = new ArrayList<>();
    properties
        .sources()
        .forEach(
            (name, settings) -> {
              ProductDataSource<?> source = byName.remove(name);
              if (source == null) {
                throw new IllegalStateException(
                    "aggregator.sources." + name + " is configured but no source is named " + name);
              }
              guarded.add(
                  guard(
                      source,
                      new SourcePolicy(settings.timeout(), settings.typicalLatency()),
                      breakers.circuitBreaker(name)));
            });
    if (!byName.isEmpty()) {
      String name = byName.keySet().iterator().next();
      throw new IllegalStateException(
          "source " + name + " has no configuration: add aggregator.sources." + name);
    }
    return new ProductAggregationService(
        guarded,
        upstreamExecutor,
        properties.globalDeadline(),
        listener.getIfAvailable(() -> AggregationListener.NONE));
  }

  @Bean
  ProductViewService productViewService(
      AggregatorProperties properties,
      MarketRegistry markets,
      ProductAggregationService aggregation,
      MessageSource messages) {
    Labels labels = new Labels(messages);
    requireLabelBundles(markets, labels);
    return new ProductViewService(
        markets,
        aggregation,
        new ProductViewAssembler(labels),
        properties.circuitBreaker().waitInOpenState());
  }

  /**
   * A market whose language has no bundle would get the base bundle's English labels without any
   * error, so it is a deployment mistake and stops startup.
   */
  private static void requireLabelBundles(MarketRegistry markets, Labels labels) {
    for (String code : markets.supportedCodes()) {
      Market market = markets.resolve(code);
      if (!labels.hasBundleFor(market.locale())) {
        throw new IllegalStateException(
            "market "
                + code
                + " has no label bundle for its language "
                + market.language()
                + ": add messages_"
                + market.language()
                + ".properties with bundle.language="
                + market.language());
      }
    }
  }

  private static <T> GuardedSource<T> guard(
      ProductDataSource<T> source, SourcePolicy policy, CircuitBreaker breaker) {
    return new GuardedSource<>(source, policy, breaker, System::nanoTime);
  }
}
