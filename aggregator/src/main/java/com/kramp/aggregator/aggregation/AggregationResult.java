package com.kramp.aggregator.aggregation;

import java.time.Duration;
import java.util.Map;

/**
 * Every source's outcome, in configuration order, for a request whose required sources answered.
 */
public record AggregationResult(
    ProductRequest request,
    Map<String, SourceOutcome<?>> outcomes,
    boolean degraded,
    Duration duration) {

  @SuppressWarnings("unchecked")
  public <T> SourceOutcome<T> outcome(String source) {
    SourceOutcome<?> outcome = outcomes.get(source);
    if (outcome == null) {
      throw new IllegalArgumentException("no source named " + source);
    }
    return (SourceOutcome<T>) outcome;
  }
}
