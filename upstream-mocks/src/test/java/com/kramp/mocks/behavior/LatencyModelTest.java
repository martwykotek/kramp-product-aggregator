package com.kramp.mocks.behavior;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LatencyModelTest {

  @Nested
  class Sample {

    // proves: REQ-MOCK-UPSTREAM-001
    @Test
    void shouldCentreOnMedianWithLongTailWhenSampledManyTimes() {
      // Given
      Random random = new Random(42);
      Duration median = Duration.ofMillis(100);

      // When
      double[] millis = new double[10_000];
      for (int i = 0; i < millis.length; i++) {
        millis[i] = LatencyModel.sample(median, random).toNanos() / 1_000_000.0;
      }
      Arrays.sort(millis);

      // Then
      assertThat(millis[5_000]).isBetween(90.0, 110.0);
      assertThat(millis[9_900]).isBetween(240.0, 360.0);
    }
  }
}
