package com.kramp.aggregator.aggregation;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    long start = System.nanoTime();
    Map<String, SourceOutcome<?>> outcomes = new LinkedHashMap<>();
    Map<String, Future<? extends SourceOutcome<?>>> running = new HashMap<>();
    for (GuardedSource<?> source : sources) {
      if (source.isApplicable(request)) {
        outcomes.put(source.name(), null);
        running.put(source.name(), executor.submit(() -> source.call(request)));
      } else {
        outcomes.put(source.name(), new SourceOutcome.Skipped<>());
      }
    }
    for (GuardedSource<?> source : waitOrder) {
      Future<? extends SourceOutcome<?>> future = running.get(source.name());
      if (future == null) {
        continue;
      }
      SourceOutcome<?> outcome = await(future, deadlineOf(source, start), start, running);
      outcomes.put(source.name(), outcome);
      if (source.criticality() == Criticality.REQUIRED && !(outcome instanceof SourceOutcome.Ok)) {
        cancelUnfinished(running, outcomes, start);
        RequiredSourceFailedException failure =
            new RequiredSourceFailedException(
                request,
                source.name(),
                outcome,
                Collections.unmodifiableMap(outcomes),
                since(start));
        listener.failed(failure);
        throw failure;
      }
    }
    boolean degraded =
        sources.stream()
            .anyMatch(
                source ->
                    source.criticality() == Criticality.OPTIONAL
                        && outcomes.get(source.name()) instanceof SourceOutcome.Failed);
    AggregationResult result =
        new AggregationResult(
            request, Collections.unmodifiableMap(outcomes), degraded, since(start));
    listener.completed(result);
    return result;
  }

  private long deadlineOf(GuardedSource<?> source, long start) {
    long ownDeadline = source.policy().timeout().plus(DEADLINE_GRACE).toNanos();
    return start + Math.min(ownDeadline, globalDeadline.toNanos());
  }

  private static SourceOutcome<?> await(
      Future<? extends SourceOutcome<?>> future,
      long deadline,
      long start,
      Map<String, Future<? extends SourceOutcome<?>>> running) {
    try {
      return future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
    } catch (TimeoutException late) {
      future.cancel(true);
      return new SourceOutcome.Failed<>(FailureKind.TIMEOUT, since(start));
    } catch (ExecutionException bug) {
      // GuardedSource never throws; only an Error inside a source gets here.
      return new SourceOutcome.Failed<>(FailureKind.ERROR, since(start));
    } catch (InterruptedException interrupted) {
      running.values().forEach(other -> other.cancel(true));
      Thread.currentThread().interrupt();
      throw new CancellationException("aggregation interrupted");
    }
  }

  /**
   * Finished calls keep their outcome; the rest are interrupted. A call already past its own
   * deadline timed out; any other was cancelled because the request had failed without it.
   */
  private void cancelUnfinished(
      Map<String, Future<? extends SourceOutcome<?>>> running,
      Map<String, SourceOutcome<?>> outcomes,
      long start) {
    long now = System.nanoTime();
    for (GuardedSource<?> source : sources) {
      Future<? extends SourceOutcome<?>> future = running.get(source.name());
      if (future == null || outcomes.get(source.name()) != null) {
        continue;
      }
      if (future.state() == Future.State.SUCCESS) {
        outcomes.put(source.name(), future.resultNow());
        continue;
      }
      future.cancel(true);
      FailureKind kind =
          now - deadlineOf(source, start) >= 0 ? FailureKind.TIMEOUT : FailureKind.CANCELLED;
      outcomes.put(source.name(), new SourceOutcome.Failed<>(kind, since(start)));
    }
  }

  private static Duration since(long start) {
    return Duration.ofNanos(System.nanoTime() - start);
  }
}
