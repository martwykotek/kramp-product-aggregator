package com.kramp.aggregator.aggregation;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.task.AsyncTaskExecutor;

/**
 * Calls every applicable source in parallel on virtual threads and waits for each one only until
 * its own deadline (budget + grace) or the global deadline, whichever comes first. A REQUIRED
 * source that does not answer with data ends the request at once and the others are cancelled.
 */
public final class ProductAggregationService {

  /**
   * Lets a source's own HTTP timeout fire, and be recorded by its breaker, before we stop waiting.
   */
  public static final Duration DEADLINE_GRACE = Duration.ofMillis(25);

  private final List<GuardedSource<?>> sources; // declaration order = meta.sources order
  private final List<GuardedSource<?>> waitOrder; // REQUIRED first, so their failure is seen first
  private final AsyncTaskExecutor executor;
  private final Duration globalDeadline;
  private final AggregationListener listener;

  public ProductAggregationService(
      List<GuardedSource<?>> sources,
      AsyncTaskExecutor executor,
      Duration globalDeadline,
      AggregationListener listener) {
    this.sources = List.copyOf(sources);
    this.waitOrder =
        sources.stream()
            .sorted(
                Comparator.comparing(
                    source -> source.criticality() == Criticality.REQUIRED ? 0 : 1))
            .toList();
    this.executor = executor;
    this.globalDeadline = globalDeadline;
    this.listener = listener;
  }

  public AggregationResult aggregate(ProductRequest request) {
    throw new UnsupportedOperationException("not implemented");
  }
}
