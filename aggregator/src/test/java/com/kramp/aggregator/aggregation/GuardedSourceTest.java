package com.kramp.aggregator.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.kramp.aggregator.testing.FakeNanoClock;
import com.kramp.aggregator.testing.FakeSource;
import com.kramp.aggregator.testing.FakeSource.Step;
import com.kramp.aggregator.testing.MutableClock;
import com.kramp.aggregator.testing.TestProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GuardedSourceTest {

  static final SourcePolicy PRICING =
      new SourcePolicy(Duration.ofMillis(300), Duration.ofMillis(80));
  static final ProductRequest REQUEST =
      new ProductRequest("W6103", TestProperties.market("pl-PL"), Optional.of("C-1002"));

  final MutableClock clock = new MutableClock();
  final FakeNanoClock nanos = new FakeNanoClock();
  final CircuitBreaker breaker = CircuitBreaker.of("pricing", TestProperties.breakerConfig(clock));

  static Duration ms(long millis) {
    return Duration.ofMillis(millis);
  }

  GuardedSource<String> guard(FakeSource<String> fake) {
    return new GuardedSource<>(fake.advancing(nanos), PRICING, breaker, nanos);
  }

  /** 10 answers, then 10 timeouts: a timeout is never retried, so one call is one invocation. */
  GuardedSource<String> opened(FakeSource<String> fake) {
    GuardedSource<String> guarded = guard(fake);
    for (int i = 0; i < 20; i++) {
      guarded.call(REQUEST);
    }
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    return guarded;
  }

  @Nested
  class Retry {

    static Stream<Arguments> fastFailureScripts() {
      return Stream.of(
          // Case A: first call fails fast, the retry answers
          arguments(
              List.of(Step.<String>unavailable(ms(10)), Step.value("quote", ms(20))),
              SourceStatus.OK,
              2),
          // Case C: both calls fail fast; there is never a third
          arguments(
              List.of(
                  Step.<String>unavailable(ms(10)),
                  Step.<String>unavailable(ms(10)),
                  Step.value("quote", ms(20))),
              SourceStatus.ERROR,
              2));
    }

    // proves: REQ-AGG-RES-012
    @ParameterizedTest
    @MethodSource("fastFailureScripts")
    void shouldRetryOnceWhenFastFailureLeavesBudget(
        List<Step<String>> script, SourceStatus expected, int invocations) {
      // Given
      FakeSource<String> fake =
          FakeSource.<String>optional("pricing").then(script.toArray(Step[]::new));

      // When
      SourceOutcome<String> outcome = guard(fake).call(REQUEST);

      // Then
      assertThat(outcome.status()).isEqualTo(expected);
      assertThat(fake.invocations()).isEqualTo(invocations);
    }

    // proves: REQ-AGG-RES-012
    @Test
    void shouldNotRetryWhenRemainingBudgetBelowTypicalLatency() {
      // Given — fails after 230 ms: 70 ms left is less than the typical 80 ms
      FakeSource<String> fake =
          FakeSource.optional(
              "pricing", Step.<String>unavailable(ms(230)), Step.value("quote", ms(20)));

      // When
      SourceOutcome<String> outcome = guard(fake).call(REQUEST);

      // Then
      assertThat(outcome)
          .isInstanceOfSatisfying(
              SourceOutcome.Failed.class,
              failed -> assertThat(failed.kind()).isEqualTo(FailureKind.ERROR));
      assertThat(fake.invocations()).isEqualTo(1);
    }

    static Stream<Arguments> answersNeverRetried() {
      return Stream.of(
          arguments(Step.<String>timeout(ms(300)), SourceStatus.TIMEOUT),
          arguments(Step.<String>notFound(ms(10)), SourceStatus.NOT_FOUND));
    }

    // proves: REQ-AGG-RES-013
    @ParameterizedTest
    @MethodSource("answersNeverRetried")
    void shouldNotRetryWhenTimeoutOrNotFound(Step<String> first, SourceStatus expected) {
      // Given
      FakeSource<String> fake = FakeSource.optional("pricing", first, Step.value("quote", ms(20)));

      // When
      SourceOutcome<String> outcome = guard(fake).call(REQUEST);

      // Then
      assertThat(outcome.status()).isEqualTo(expected);
      assertThat(fake.invocations()).isEqualTo(1);
    }

    @Test
    void shouldNotRetryWhenFailureIsNotRetryable() {
      // Given — e.g. an unexpected 429 from the upstream (Review Focus 3)
      FakeSource<String> fake =
          FakeSource.optional(
              "pricing", Step.<String>unavailableNotRetryable(ms(10)), Step.value("quote", ms(20)));

      // When
      SourceOutcome<String> outcome = guard(fake).call(REQUEST);

      // Then
      assertThat(outcome.status()).isEqualTo(SourceStatus.ERROR);
      assertThat(fake.invocations()).isEqualTo(1);
    }
  }

  @Nested
  class CircuitBreaking {

    // proves: REQ-AGG-RES-014
    @Test
    void shouldOpenCircuitWhenHalfOfLastTwentyCallsFail() {
      // Given
      FakeSource<String> fake =
          FakeSource.<String>optional("pricing")
              .then(10, Step.value("quote", ms(5)))
              .then(10, Step.timeout(ms(300)));
      GuardedSource<String> guarded = opened(fake);

      // When
      SourceOutcome<String> outcome = guarded.call(REQUEST);

      // Then
      assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
      assertThat(outcome.status()).isEqualTo(SourceStatus.CIRCUIT_OPEN);
      assertThat(fake.invocations()).isEqualTo(20);
    }

    // proves: REQ-AGG-RES-014
    @Test
    void shouldStayClosedWhenFailureRateBelowHalf() {
      // Given — 9 of 19 calls fail (47%)
      FakeSource<String> fake =
          FakeSource.<String>optional("pricing")
              .then(10, Step.value("quote", ms(5)))
              .then(9, Step.timeout(ms(300)));
      GuardedSource<String> guarded = guard(fake);

      // When
      for (int i = 0; i < 19; i++) {
        guarded.call(REQUEST);
      }

      // Then
      assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // proves: REQ-AGG-RES-016
    @Test
    void shouldResumeAfterOpenPeriodWhenTrialCallsSucceed() {
      // Given — open, and the upstream has recovered
      FakeSource<String> fake =
          FakeSource.<String>optional("pricing")
              .then(10, Step.value("quote", ms(5)))
              .then(10, Step.timeout(ms(300)))
              .then(Step.value("quote", ms(5)));
      GuardedSource<String> guarded = opened(fake);

      // When
      clock.advance(Duration.ofSeconds(9));
      SourceOutcome<String> stillOpen = guarded.call(REQUEST);
      int invocationsWhileOpen = fake.invocations();
      clock.advance(Duration.ofSeconds(2));
      List<SourceOutcome<String>> trials =
          List.of(guarded.call(REQUEST), guarded.call(REQUEST), guarded.call(REQUEST));

      // Then
      assertThat(stillOpen.status()).isEqualTo(SourceStatus.CIRCUIT_OPEN);
      assertThat(invocationsWhileOpen).isEqualTo(20);
      assertThat(trials).extracting(SourceOutcome::status).containsOnly(SourceStatus.OK);
      assertThat(fake.invocations()).isEqualTo(23);
      assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // proves: REQ-AGG-RES-016
    @Test
    void shouldReopenWhenTrialCallsFail() {
      // Given — open; at +11 s two of the three trial calls fail (oracle per Q1, option a)
      FakeSource<String> fake =
          FakeSource.<String>optional("pricing")
              .then(10, Step.value("quote", ms(5)))
              .then(12, Step.timeout(ms(300)))
              .then(Step.value("quote", ms(5)));
      GuardedSource<String> guarded = opened(fake);
      clock.advance(Duration.ofSeconds(11));

      // When
      guarded.call(REQUEST);
      guarded.call(REQUEST);
      CircuitBreaker.State afterTwoTrials = breaker.getState();
      guarded.call(REQUEST);

      // Then
      assertThat(afterTwoTrials).isEqualTo(CircuitBreaker.State.HALF_OPEN);
      assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    // proves: REQ-AGG-RES-017
    @Test
    void shouldIgnoreNotFoundWhenCountingFailures() {
      // Given
      FakeSource<String> fake = FakeSource.optional("pricing", Step.<String>notFound(ms(5)));
      GuardedSource<String> guarded = guard(fake);

      // When
      List<SourceOutcome<String>> outcomes = new ArrayList<>();
      for (int i = 0; i < 30; i++) {
        outcomes.add(guarded.call(REQUEST));
      }

      // Then
      assertThat(outcomes).extracting(SourceOutcome::status).containsOnly(SourceStatus.NOT_FOUND);
      assertThat(fake.invocations()).isEqualTo(30);
      assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // proves: RC-AGG-RES-002
    @Test
    void shouldNotCountCancellationWhenRequestAlreadyFailed() throws InterruptedException {
      // Given — a slow upstream and real time: the orchestrator interrupts the calling thread
      FakeSource<String> hanging =
          FakeSource.optional("pricing", Step.value("quote", Duration.ofSeconds(2)));
      GuardedSource<String> guarded =
          new GuardedSource<>(hanging, PRICING, breaker, System::nanoTime);
      List<SourceOutcome<String>> outcomes = new CopyOnWriteArrayList<>();

      // When — cancelled 15 times, as when Catalog has already failed
      for (int i = 0; i < 15; i++) {
        Thread caller = Thread.ofVirtual().start(() -> outcomes.add(guarded.call(REQUEST)));
        Thread.sleep(20);
        caller.interrupt();
        caller.join();
      }

      // Then
      assertThat(outcomes)
          .hasSize(15)
          .extracting(SourceOutcome::status)
          .containsOnly(SourceStatus.TIMEOUT);
      assertThat(hanging.interruptions()).isEqualTo(15);
      assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
      assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
      assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).isZero();
    }
  }
}
