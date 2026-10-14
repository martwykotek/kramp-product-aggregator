package com.kramp.aggregator.observability;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Comparator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Shows each upstream's breaker state in /actuator/health. Always UP: a failing upstream degrades
 * responses but must never take the aggregator out of the load balancer, so this indicator stays
 * out of the liveness and readiness groups as well (REQ-OPS-OBS-005).
 */
public final class UpstreamsHealthIndicator implements HealthIndicator {

  private final CircuitBreakerRegistry breakers;

  public UpstreamsHealthIndicator(CircuitBreakerRegistry breakers) {
    this.breakers = breakers;
  }

  @Override
  public Health health() {
    Health.Builder health = Health.up();
    breakers.getAllCircuitBreakers().stream()
        .sorted(Comparator.comparing(CircuitBreaker::getName))
        .forEach(breaker -> health.withDetail(breaker.getName(), breaker.getState().name()));
    return health.build();
  }
}
