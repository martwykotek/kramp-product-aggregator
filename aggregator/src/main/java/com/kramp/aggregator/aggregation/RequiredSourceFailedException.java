package com.kramp.aggregator.aggregation;

import java.time.Duration;
import java.util.Map;

/** A REQUIRED source did not answer with data, so there is no product view to build. */
public final class RequiredSourceFailedException extends RuntimeException {

  private final ProductRequest request;
  private final String source;
  private final SourceOutcome<?> outcome;
  private final Map<String, SourceOutcome<?>> outcomes;
  private final Duration duration;

  public RequiredSourceFailedException(
      ProductRequest request,
      String source,
      SourceOutcome<?> outcome,
      Map<String, SourceOutcome<?>> outcomes,
      Duration duration) {
    super("required source " + source + " ended with " + outcome.status());
    this.request = request;
    this.source = source;
    this.outcome = outcome;
    this.outcomes = outcomes;
    this.duration = duration;
  }

  public ProductRequest request() {
    return request;
  }

  public String source() {
    return source;
  }

  public SourceOutcome<?> outcome() {
    return outcome;
  }

  /**
   * Every source's outcome; calls cut short by this failure are CANCELLED (TIMEOUT if past their
   * deadline).
   */
  public Map<String, SourceOutcome<?>> outcomes() {
    return outcomes;
  }

  public Duration duration() {
    return duration;
  }
}
