package com.kramp.aggregator.aggregation;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One upstream call under its policy: circuit breaker first, then the call, then at most one retry
 * after a fast failure if the remaining budget still covers a typical response. Never throws.
 */
public final class GuardedSource<T> {

  private static final Logger log = LoggerFactory.getLogger(GuardedSource.class);

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
    long start = nanoTime.getAsLong();
    for (int attempt = 1; ; attempt++) {
      if (!breaker.tryAcquirePermission()) {
        return new SourceOutcome.Failed<>(FailureKind.CIRCUIT_OPEN, since(start));
      }
      long attemptStart = nanoTime.getAsLong();
      try {
        T value = source.fetch(request);
        breaker.onSuccess(nanoTime.getAsLong() - attemptStart, TimeUnit.NANOSECONDS);
        return new SourceOutcome.Ok<>(value, since(start));
      } catch (UpstreamNotFoundException notFound) {
        // "No such product/price/customer" is a business answer, not a sign of an unhealthy
        // upstream.
        breaker.releasePermission();
        return new SourceOutcome.NotFound<>(notFound.reason(), since(start));
      } catch (RuntimeException failure) {
        if (Thread.currentThread().isInterrupted()) {
          // Cancelled by the orchestrator because the request was already decided; says nothing
          // about this upstream's health (RC-AGG-RES-002). Reported as TIMEOUT for the log only.
          breaker.releasePermission();
          return new SourceOutcome.Failed<>(FailureKind.TIMEOUT, since(start));
        }
        breaker.onError(nanoTime.getAsLong() - attemptStart, TimeUnit.NANOSECONDS, failure);
        log.warn(
            "upstream_call_failed source={} attempt={} reason={}",
            source.name(),
            attempt,
            describe(failure));
        if (failure instanceof UpstreamTimeoutException) {
          return new SourceOutcome.Failed<>(FailureKind.TIMEOUT, since(start));
        }
        if (attempt == 1
            && isRetryable(failure)
            && remaining(start) >= policy.typicalLatency().toNanos()) {
          continue;
        }
        return new SourceOutcome.Failed<>(FailureKind.ERROR, since(start));
      }
    }
  }

  private static boolean isRetryable(RuntimeException failure) {
    return failure instanceof UpstreamUnavailableException unavailable && unavailable.retryable();
  }

  /** Upstream exception messages are built by our adapters and carry no URL or customer id. */
  private static String describe(RuntimeException failure) {
    return failure instanceof UpstreamException
        ? failure.getMessage()
        : failure.getClass().getSimpleName();
  }

  private long remaining(long start) {
    return policy.timeout().toNanos() - (nanoTime.getAsLong() - start);
  }

  private Duration since(long start) {
    return Duration.ofNanos(nanoTime.getAsLong() - start);
  }
}
