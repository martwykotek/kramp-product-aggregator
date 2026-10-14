package com.kramp.aggregator.config;

import com.kramp.aggregator.observability.AggregationTelemetry;
import com.kramp.aggregator.observability.UpstreamsHealthIndicator;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Summary log line, upstream and breaker metrics, and the `upstreams` health details. */
@Configuration(proxyBeanMethods = false)
class ObservabilityConfig {

  @Bean
  AggregationTelemetry aggregationTelemetry(MeterRegistry meters) {
    return new AggregationTelemetry(meters);
  }

  @Bean
  MeterBinder circuitBreakerMetrics(CircuitBreakerRegistry breakers) {
    return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(breakers);
  }

  /** The bean name makes the health component `upstreams`. */
  @Bean
  UpstreamsHealthIndicator upstreamsHealthIndicator(CircuitBreakerRegistry breakers) {
    return new UpstreamsHealthIndicator(breakers);
  }
}
