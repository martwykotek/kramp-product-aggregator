package com.kramp.aggregator.aggregation;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.function.LongSupplier;

/**
 * One upstream call under its policy: circuit breaker first, then the call, then at most one retry
 * after a fast failure if the remaining budget still covers a typical response. Never throws.
 */
public final class GuardedSource<T> {

  private final ProductDataSource<T> source;
  private final SourcePolicy policy;
  private final CircuitBreaker breaker;
  private final LongSupplier nanoTime;

  public GuardedSource(
      ProductDataSource<T> source,
      SourcePolicy policy,
      CircuitBreaker breaker,
      LongSupplier nanoTime) {
    this.source = source;
    this.policy = policy;
    this.breaker = breaker;
    this.nanoTime = nanoTime;
  }

  public String name() {
    return source.name();
  }

  public Criticality criticality() {
    return source.criticality();
  }

  public SourcePolicy policy() {
    return policy;
  }

  public boolean isApplicable(ProductRequest request) {
    return source.isApplicable(request);
  }

  public SourceOutcome<T> call(ProductRequest request) {
    throw new UnsupportedOperationException("not implemented");
  }
}
