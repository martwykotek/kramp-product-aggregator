package com.kramp.aggregator.testing;

import com.kramp.aggregator.aggregation.Criticality;
import com.kramp.aggregator.aggregation.ProductDataSource;
import com.kramp.aggregator.aggregation.ProductRequest;
import com.kramp.aggregator.aggregation.UpstreamNotFoundException;
import com.kramp.aggregator.aggregation.UpstreamTimeoutException;
import com.kramp.aggregator.aggregation.UpstreamUnavailableException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/** A ProductDataSource that plays a script: one step per invocation, the last step repeats. */
public final class FakeSource<T> implements ProductDataSource<T> {

  public sealed interface Step<T> {
    Duration after();

    record Value<T>(T value, Duration after) implements Step<T> {}

    record NotFound<T>(String reason, Duration after) implements Step<T> {}

    record Unavailable<T>(Duration after, boolean retryable) implements Step<T> {}

    record Timeout<T>(Duration after) implements Step<T> {}

    static <T> Step<T> value(T value, Duration after) {
      return new Value<>(value, after);
    }

    static <T> Step<T> notFound(Duration after) {
      return new NotFound<>("PRODUCT_NOT_FOUND", after);
    }

    static <T> Step<T> notFound(String reason, Duration after) {
      return new NotFound<>(reason, after);
    }

    static <T> Step<T> unavailable(Duration after) {
      return new Unavailable<>(after, true);
    }

    static <T> Step<T> unavailableNotRetryable(Duration after) {
      return new Unavailable<>(after, false);
    }

    static <T> Step<T> timeout(Duration after) {
      return new Timeout<>(after);
    }
  }

  private final String name;
  private final Criticality criticality;
  private final List<Step<T>> script = new CopyOnWriteArrayList<>();
  private volatile IntFunction<Step<T>> stepForInvocation = this::scripted;
  private volatile Predicate<ProductRequest> applicable = request -> true;
  private volatile FakeNanoClock clock;
  private final AtomicInteger invocations = new AtomicInteger();
  private final AtomicInteger interruptions = new AtomicInteger();

  private FakeSource(String name, Criticality criticality) {
    this.name = name;
    this.criticality = criticality;
  }

  @SafeVarargs
  public static <T> FakeSource<T> required(String name, Step<T>... steps) {
    return new FakeSource<T>(name, Criticality.REQUIRED).then(steps);
  }

  @SafeVarargs
  public static <T> FakeSource<T> optional(String name, Step<T>... steps) {
    return new FakeSource<T>(name, Criticality.OPTIONAL).then(steps);
  }

  @SafeVarargs
  public final FakeSource<T> then(Step<T>... steps) {
    script.addAll(List.of(steps));
    return this;
  }

  public FakeSource<T> then(int times, Step<T> step) {
    script.addAll(Collections.nCopies(times, step));
    return this;
  }

  public FakeSource<T> withSteps(IntFunction<Step<T>> steps) {
    this.stepForInvocation = steps;
    return this;
  }

  public FakeSource<T> applicableWhen(Predicate<ProductRequest> predicate) {
    this.applicable = predicate;
    return this;
  }

  /** Instead of sleeping, each step advances this fake clock (deterministic budgets). */
  public FakeSource<T> advancing(FakeNanoClock clock) {
    this.clock = clock;
    return this;
  }

  public int invocations() {
    return invocations.get();
  }

  public int interruptions() {
    return interruptions.get();
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Criticality criticality() {
    return criticality;
  }

  @Override
  public boolean isApplicable(ProductRequest request) {
    return applicable.test(request);
  }

  @Override
  public T fetch(ProductRequest request) {
    Step<T> step = stepForInvocation.apply(invocations.getAndIncrement());
    pass(step.after());
    return switch (step) {
      case Step.Value<T> value -> value.value();
      case Step.NotFound<T> notFound ->
          throw new UpstreamNotFoundException(name, notFound.reason());
      case Step.Unavailable<T> unavailable ->
          throw new UpstreamUnavailableException(
              name, name + " answered 503", unavailable.retryable());
      case Step.Timeout<T> timeout ->
          throw new UpstreamTimeoutException(name, name + " did not answer within its budget");
    };
  }

  private Step<T> scripted(int invocation) {
    return script.get(Math.min(invocation, script.size() - 1));
  }

  private void pass(Duration duration) {
    if (clock != null) {
      clock.advance(duration);
      return;
    }
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      interruptions.incrementAndGet();
      Thread.currentThread().interrupt();
      throw new UpstreamUnavailableException(name, name + " call interrupted", true);
    }
  }
}
