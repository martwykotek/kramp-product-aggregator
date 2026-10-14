package com.kramp.mocks.behavior;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UpstreamBehaviorTest {

  static BehaviorProperties properties(double reliability) {
    BehaviorProperties.Profile profile =
        new BehaviorProperties.Profile(Duration.ofMillis(80), reliability);
    Map<Upstream, BehaviorProperties.Profile> upstreams = new EnumMap<>(Upstream.class);
    for (Upstream upstream : Upstream.values()) {
      upstreams.put(upstream, profile);
    }
    return new BehaviorProperties(
        null,
        Duration.ofSeconds(2),
        Duration.ofSeconds(5),
        new FailureMix(0.5, 0.3, 0.2),
        upstreams);
  }

  static List<Decision> decisions(UpstreamBehavior behavior, Upstream upstream, int count) {
    List<Decision> decisions = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      decisions.add(behavior.decide(upstream));
    }
    return decisions;
  }

  @Nested
  class Decide {

    // proves: REQ-MOCK-UPSTREAM-002
    @Test
    void shouldFailAtOneMinusReliabilityWhenHealthy() {
      // Given
      UpstreamBehavior behavior =
          new UpstreamBehavior(properties(0.98), new ChaosRegistry(), new Random(1));

      // When
      long failures =
          decisions(behavior, Upstream.AVAILABILITY, 100_000).stream()
              .filter(Decision.Fail.class::isInstance)
              .count();

      // Then
      assertThat(failures / 100_000.0).isBetween(0.017, 0.023);
    }

    // proves: REQ-MOCK-UPSTREAM-003
    @Test
    void shouldSplitFailuresByConfiguredMixWhenFailing() {
      // Given
      UpstreamBehavior behavior =
          new UpstreamBehavior(properties(0.5), new ChaosRegistry(), new Random(2));

      // When
      List<Decision.Fail> failures =
          decisions(behavior, Upstream.PRICING, 100_000).stream()
              .filter(Decision.Fail.class::isInstance)
              .map(Decision.Fail.class::cast)
              .toList();
      Map<FailureKind, Integer> counts = new EnumMap<>(FailureKind.class);
      failures.forEach(fail -> counts.merge(fail.kind(), 1, Integer::sum));

      // Then
      double total = failures.size();
      assertThat(counts.get(FailureKind.FAST_UNAVAILABLE) / total).isBetween(0.48, 0.52);
      assertThat(counts.get(FailureKind.SLOW_ERROR) / total).isBetween(0.28, 0.32);
      assertThat(counts.get(FailureKind.HANG) / total).isBetween(0.18, 0.22);
      assertThat(failures)
          .filteredOn(fail -> fail.kind() == FailureKind.FAST_UNAVAILABLE)
          .extracting(Decision::delay)
          .containsOnly(Duration.ofMillis(5));
      assertThat(failures)
          .filteredOn(fail -> fail.kind() == FailureKind.HANG)
          .extracting(Decision::delay)
          .allSatisfy(
              delay -> assertThat(delay).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(5)));
    }

    // proves: REQ-MOCK-UPSTREAM-004
    @Test
    void shouldReproduceSequenceWhenSeedIsTheSame() {
      // Given
      UpstreamBehavior first =
          new UpstreamBehavior(properties(0.9), new ChaosRegistry(), new Random(7));
      UpstreamBehavior second =
          new UpstreamBehavior(properties(0.9), new ChaosRegistry(), new Random(7));
      UpstreamBehavior otherSeed =
          new UpstreamBehavior(properties(0.9), new ChaosRegistry(), new Random(8));

      // When
      List<Decision> a = decisions(first, Upstream.CATALOG, 1_000);
      List<Decision> b = decisions(second, Upstream.CATALOG, 1_000);
      List<Decision> c = decisions(otherSeed, Upstream.CATALOG, 1_000);

      // Then
      assertThat(a).isEqualTo(b);
      assertThat(c).isNotEqualTo(a);
    }

    @Test
    void shouldFailFastWhenChaosModeIsDown() {
      // Given
      ChaosRegistry chaos = new ChaosRegistry();
      chaos.set(Upstream.PRICING, ChaosSetting.of(ChaosMode.DOWN, null, null));
      UpstreamBehavior behavior = new UpstreamBehavior(properties(1.0), chaos, new Random(3));

      // When
      Decision decision = behavior.decide(Upstream.PRICING);

      // Then
      assertThat(decision)
          .isEqualTo(new Decision.Fail(FailureKind.FAST_UNAVAILABLE, Duration.ofMillis(5)));
    }

    @Test
    void shouldHangWhenChaosModeIsTimeout() {
      // Given
      ChaosRegistry chaos = new ChaosRegistry();
      chaos.set(Upstream.PRICING, ChaosSetting.of(ChaosMode.TIMEOUT, null, null));
      UpstreamBehavior behavior = new UpstreamBehavior(properties(1.0), chaos, new Random(4));

      // When
      Decision decision = behavior.decide(Upstream.PRICING);

      // Then
      assertThat(decision)
          .isInstanceOfSatisfying(
              Decision.Fail.class, fail -> assertThat(fail.kind()).isEqualTo(FailureKind.HANG));
      assertThat(decision.delay()).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(5));
    }

    @Test
    void shouldMultiplyLatencyWhenChaosModeIsSlow() {
      // Given
      ChaosRegistry chaos = new ChaosRegistry();
      chaos.set(Upstream.PRICING, ChaosSetting.of(ChaosMode.SLOW, 5.0, null));
      UpstreamBehavior behavior = new UpstreamBehavior(properties(1.0), chaos, new Random(5));

      // When
      double[] millis =
          decisions(behavior, Upstream.PRICING, 2_000).stream()
              .mapToDouble(decision -> decision.delay().toNanos() / 1_000_000.0)
              .sorted()
              .toArray();

      // Then — median 80 ms × 5
      assertThat(millis[1_000]).isBetween(320.0, 480.0);
    }

    @Test
    void shouldUseOverriddenReliabilityWhenChaosModeIsFlaky() {
      // Given
      ChaosRegistry chaos = new ChaosRegistry();
      chaos.set(Upstream.CUSTOMER, ChaosSetting.of(ChaosMode.FLAKY, null, 0.5));
      UpstreamBehavior behavior = new UpstreamBehavior(properties(1.0), chaos, new Random(6));

      // When
      long failures =
          decisions(behavior, Upstream.CUSTOMER, 10_000).stream()
              .filter(Decision.Fail.class::isInstance)
              .count();

      // Then
      assertThat(failures / 10_000.0).isBetween(0.47, 0.53);
    }
  }
}
