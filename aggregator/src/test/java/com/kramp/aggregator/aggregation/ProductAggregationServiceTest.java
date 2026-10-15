package com.kramp.aggregator.aggregation;

import static com.kramp.aggregator.testing.Timing.SCHEDULING_TOLERANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.kramp.aggregator.testing.Awaits;
import com.kramp.aggregator.testing.FakeSource;
import com.kramp.aggregator.testing.FakeSource.Step;
import com.kramp.aggregator.testing.MutableClock;
import com.kramp.aggregator.testing.TestProperties;
import com.kramp.aggregator.testing.Timing;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

class ProductAggregationServiceTest {

  static final ProductRequest WITH_CUSTOMER =
      new ProductRequest("W6103", TestProperties.market("pl-PL"), Optional.of("C-1002"));
  static final ProductRequest WITHOUT_CUSTOMER =
      new ProductRequest("W6103", TestProperties.market("pl-PL"), Optional.empty());

  final MutableClock clock = new MutableClock();
  final Map<String, CircuitBreaker> breakers = new HashMap<>();
  final SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("test-upstream-");

  ProductAggregationServiceTest() {
    executor.setVirtualThreads(true);
  }

  static Duration ms(long millis) {
    return Duration.ofMillis(millis);
  }

  static FakeSource<String> catalog(Step<String> step) {
    return FakeSource.required("catalog", step);
  }

  static FakeSource<String> optional(String name, Step<String> step) {
    FakeSource<String> fake = FakeSource.optional(name, step);
    return name.equals("customer")
        ? fake.applicableWhen(request -> request.customerId().isPresent())
        : fake;
  }

  ProductAggregationService service(FakeSource<?>... fakes) {
    return service(Duration.ofMillis(500), fakes);
  }

  ProductAggregationService service(Duration globalDeadline, FakeSource<?>... fakes) {
    List<GuardedSource<?>> guarded =
        Arrays.stream(fakes).<GuardedSource<?>>map(this::guard).toList();
    return new ProductAggregationService(
        guarded, executor, globalDeadline, AggregationListener.NONE);
  }

  private <T> GuardedSource<T> guard(FakeSource<T> fake) {
    CircuitBreaker breaker =
        breakers.computeIfAbsent(
            fake.name(), name -> CircuitBreaker.of(name, TestProperties.breakerConfig(clock)));
    return new GuardedSource<>(fake, TestProperties.policy(fake.name()), breaker, System::nanoTime);
  }

  @Nested
  class FanOut {

    // proves: REQ-AGG-VIEW-004
    @Test
    void shouldCallSourcesConcurrentlyWhenAllApplicable() {
      // Given — sequential calls would take at least 400 ms
      ProductAggregationService service =
          service(
              catalog(Step.value("product", ms(100))),
              optional("pricing", Step.value("quote", ms(100))),
              optional("availability", Step.value("stock", ms(100))),
              optional("customer", Step.value("profile", ms(100))));
      long start = System.nanoTime();

      // When
      AggregationResult result = service.aggregate(WITH_CUSTOMER);

      // Then
      assertThat(Timing.since(start)).isLessThan(ms(100).plus(SCHEDULING_TOLERANCE));
      assertThat(result.outcomes().values())
          .extracting(SourceOutcome::status)
          .containsOnly(SourceStatus.OK);
      assertThat(result.outcomes().keySet())
          .containsExactly("catalog", "pricing", "availability", "customer");
    }

    // proves: REQ-AGG-VIEW-005
    @Test
    void shouldNotInvokeSourceWhenNotApplicable() {
      // Given
      FakeSource<String> customer = optional("customer", Step.value("profile", ms(5)));
      ProductAggregationService service =
          service(
              catalog(Step.value("product", ms(5))),
              optional("pricing", Step.value("quote", ms(5))),
              optional("availability", Step.value("stock", ms(5))),
              customer);

      // When
      AggregationResult result = service.aggregate(WITHOUT_CUSTOMER);

      // Then
      assertThat(result.outcome("customer").status()).isEqualTo(SourceStatus.SKIPPED);
      assertThat(customer.invocations()).isZero();
      assertThat(result.degraded()).isFalse();
    }
  }

  @Nested
  class RequiredSource {

    // proves: REQ-AGG-RES-004
    @Test
    void shouldFailFastWhenRequiredSourceFails() {
      // Given — catalog fails fast (and its single retry fails too); pricing would take 300 ms
      FakeSource<String> pricing = optional("pricing", Step.value("quote", ms(300)));
      ProductAggregationService service =
          service(
              catalog(Step.unavailable(ms(10))),
              pricing,
              optional("availability", Step.value("stock", ms(50))),
              optional("customer", Step.value("profile", ms(50))));
      long start = System.nanoTime();

      // When
      Throwable thrown = catchThrowable(() -> service.aggregate(WITH_CUSTOMER));

      // Then — two 10 ms catalog attempts, not pricing's 300 ms
      assertThat(Timing.since(start)).isLessThan(ms(20).plus(SCHEDULING_TOLERANCE));
      assertThat(thrown)
          .isInstanceOfSatisfying(
              RequiredSourceFailedException.class,
              failure -> {
                assertThat(failure.source()).isEqualTo("catalog");
                assertThat(failure.outcome())
                    .isInstanceOfSatisfying(
                        SourceOutcome.Failed.class,
                        failed -> assertThat(failed.kind()).isEqualTo(FailureKind.ERROR));
              });
      Awaits.until(() -> pricing.interruptions() == 1, Duration.ofSeconds(1));
    }

    // proves: RC-AGG-RES-002
    @Test
    void shouldReportUnfinishedSourcesAsCancelledUnlessPastTheirDeadline() {
      // Given — catalog hangs until its deadline (250 + 25 ms); by then customer (200 + 25 ms)
      // is past its own deadline, pricing (300 + 25 ms) and availability (350 + 25 ms) are not
      FakeSource<String> pricing = optional("pricing", Step.value("quote", Duration.ofSeconds(2)));
      FakeSource<String> customer =
          optional("customer", Step.value("profile", Duration.ofSeconds(2)));
      ProductAggregationService service =
          service(
              catalog(Step.value("product", Duration.ofSeconds(2))),
              pricing,
              optional("availability", Step.value("stock", Duration.ofSeconds(2))),
              customer);

      // When
      Throwable thrown = catchThrowable(() -> service.aggregate(WITH_CUSTOMER));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              RequiredSourceFailedException.class,
              failure ->
                  assertThat(failure.outcomes())
                      .extractingByKeys("catalog", "pricing", "availability", "customer")
                      .extracting(SourceOutcome::status)
                      .containsExactly(
                          SourceStatus.TIMEOUT,
                          SourceStatus.CANCELLED,
                          SourceStatus.CANCELLED,
                          SourceStatus.TIMEOUT));
      Awaits.until(
          () -> pricing.interruptions() == 1 && customer.interruptions() == 1,
          Duration.ofSeconds(1));
      Awaits.until(
          () -> breakers.get("customer").getMetrics().getNumberOfFailedCalls() == 1,
          Duration.ofSeconds(1));
      assertThat(breakers.get("pricing").getMetrics().getNumberOfBufferedCalls()).isZero();
      assertThat(breakers.get("availability").getMetrics().getNumberOfBufferedCalls()).isZero();
    }
  }

  @Nested
  class OptionalSources {

    static Stream<Arguments> optionalFailures() {
      return Stream.of("pricing", "availability", "customer")
          .flatMap(
              source ->
                  Stream.of(FailureKind.ERROR, FailureKind.TIMEOUT, FailureKind.CIRCUIT_OPEN)
                      .map(kind -> arguments(source, kind)));
    }

    // proves: REQ-AGG-RES-008
    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("optionalFailures")
    void shouldMarkDegradedWhenAnyCalledOptionalSourceFails(String failing, FailureKind kind) {
      // Given
      Step<String> failingStep =
          switch (kind) {
            case ERROR -> Step.unavailableNotRetryable(ms(5));
            case TIMEOUT -> Step.timeout(ms(5));
            case CIRCUIT_OPEN -> Step.value("never called", ms(5));
            case CANCELLED ->
                throw new IllegalArgumentException("only reported once the request has failed");
          };
      ProductAggregationService service =
          service(
              catalog(Step.value("product", ms(5))),
              optional(
                  "pricing", failing.equals("pricing") ? failingStep : Step.value("quote", ms(5))),
              optional(
                  "availability",
                  failing.equals("availability") ? failingStep : Step.value("stock", ms(5))),
              optional(
                  "customer",
                  failing.equals("customer") ? failingStep : Step.value("profile", ms(5))));
      if (kind == FailureKind.CIRCUIT_OPEN) {
        breakers.get(failing).transitionToOpenState();
      }

      // When
      AggregationResult result = service.aggregate(WITH_CUSTOMER);

      // Then
      assertThat(result.degraded()).isTrue();
      assertThat(result.outcome(failing).status()).isEqualTo(SourceStatus.valueOf(kind.name()));
    }

    // proves: REQ-AGG-RES-009
    @ParameterizedTest
    @ValueSource(strings = {"pricing", "availability", "customer"})
    void shouldNotMarkDegradedWhenOptionalSourceReportsNotFound(String notFound) {
      // Given
      ProductAggregationService service =
          service(
              catalog(Step.value("product", ms(5))),
              optional(
                  "pricing",
                  notFound.equals("pricing") ? Step.notFound(ms(5)) : Step.value("quote", ms(5))),
              optional(
                  "availability",
                  notFound.equals("availability")
                      ? Step.notFound(ms(5))
                      : Step.value("stock", ms(5))),
              optional(
                  "customer",
                  notFound.equals("customer")
                      ? Step.notFound(ms(5))
                      : Step.value("profile", ms(5))));

      // When
      AggregationResult result = service.aggregate(WITH_CUSTOMER);

      // Then
      assertThat(result.degraded()).isFalse();
      assertThat(result.outcome(notFound).status()).isEqualTo(SourceStatus.NOT_FOUND);
    }
  }

  @Nested
  class GlobalDeadline {

    FakeSource<String> pricing = optional("pricing", Step.value("quote", Duration.ofSeconds(2)));
    FakeSource<String> availability =
        optional("availability", Step.value("stock", Duration.ofSeconds(2)));
    FakeSource<String> customer =
        optional("customer", Step.value("profile", Duration.ofSeconds(2)));

    /** Below every source's own deadline (customer 225 ms … availability 375 ms). */
    static final Duration GLOBAL_DEADLINE = ms(100);

    // proves: REQ-AGG-RES-011
    @Test
    void shouldFinishWithinGlobalDeadlineWhenAllSourcesHang() {
      // Given
      ProductAggregationService service =
          service(
              GLOBAL_DEADLINE,
              catalog(Step.value("product", ms(10))),
              pricing,
              availability,
              customer);
      long start = System.nanoTime();

      // When
      AggregationResult result = service.aggregate(WITH_CUSTOMER);

      // Then
      assertThat(Timing.since(start)).isLessThan(GLOBAL_DEADLINE.plus(SCHEDULING_TOLERANCE));
      assertThat(
              List.of(
                  result.outcome("pricing").status(),
                  result.outcome("availability").status(),
                  result.outcome("customer").status()))
          .containsOnly(SourceStatus.TIMEOUT);
      assertThat(result.degraded()).isTrue();
    }

    @Test
    void shouldInterruptHangingSourcesWhenDeadlinePasses() {
      // Given (Review Focus 4)
      ProductAggregationService service =
          service(catalog(Step.value("product", ms(10))), pricing, availability, customer);

      // When
      service.aggregate(WITH_CUSTOMER);

      // Then
      Awaits.until(
          () ->
              pricing.interruptions() == 1
                  && availability.interruptions() == 1
                  && customer.interruptions() == 1,
          Duration.ofSeconds(1));
    }

    // proves: REQ-AGG-RES-011
    @Test
    void shouldFailWithinGlobalDeadlineWhenRequiredSourceHangs() {
      // Given
      ProductAggregationService service =
          service(
              GLOBAL_DEADLINE,
              catalog(Step.value("product", Duration.ofSeconds(2))),
              pricing,
              availability,
              customer);
      long start = System.nanoTime();

      // When
      Throwable thrown = catchThrowable(() -> service.aggregate(WITH_CUSTOMER));

      // Then
      assertThat(Timing.since(start)).isLessThan(GLOBAL_DEADLINE.plus(SCHEDULING_TOLERANCE));
      assertThat(thrown)
          .isInstanceOfSatisfying(
              RequiredSourceFailedException.class,
              failure ->
                  assertThat(failure.outcome())
                      .isInstanceOfSatisfying(
                          SourceOutcome.Failed.class,
                          failed -> assertThat(failed.kind()).isEqualTo(FailureKind.TIMEOUT)));
    }
  }

  @Nested
  class Concurrency {

    @Test
    void shouldStayConsistentWhenManyRequestsRunConcurrently() throws Exception {
      // Given — pricing fails half of the time (seeded) behind a real breaker
      Random random = new Random(42);
      FakeSource<String> pricing =
          FakeSource.<String>optional("pricing")
              .withSteps(
                  invocation ->
                      random.nextBoolean() ? Step.value("quote", ms(5)) : Step.unavailable(ms(5)));
      ProductAggregationService service =
          service(
              catalog(Step.value("product", ms(5))),
              pricing,
              optional("availability", Step.value("stock", ms(5))),
              optional("customer", Step.value("profile", ms(5))));
      List<Callable<Object>> calls =
          IntStream.range(0, 50)
              .<Callable<Object>>mapToObj(
                  i ->
                      () -> {
                        try {
                          return service.aggregate(WITH_CUSTOMER);
                        } catch (RequiredSourceFailedException failure) {
                          return failure;
                        }
                      })
              .toList();

      // When
      List<Future<Object>> futures;
      try (ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
        futures = callers.invokeAll(calls);
      }

      // Then — Future.get() throws ExecutionException for any other exception
      for (Future<Object> future : futures) {
        assertThat(future.get())
            .isInstanceOfAny(AggregationResult.class, RequiredSourceFailedException.class);
      }
      assertThat(breakers.get("pricing").getState())
          .isIn(
              CircuitBreaker.State.CLOSED,
              CircuitBreaker.State.OPEN,
              CircuitBreaker.State.HALF_OPEN);
    }
  }
}
