package com.kramp.mocks.behavior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChaosSettingTest {

  @Nested
  class Of {

    @Test
    void shouldDefaultSlowFactorToFiveWhenSlowModeHasNoFactor() {
      // Given / When
      ChaosSetting setting = ChaosSetting.of(ChaosMode.SLOW, null, null);

      // Then
      assertThat(setting.slowFactor()).isEqualTo(5.0);
    }

    @Test
    void shouldDefaultReliabilityToHalfWhenFlakyModeHasNoReliability() {
      // Given / When
      ChaosSetting setting = ChaosSetting.of(ChaosMode.FLAKY, null, null);

      // Then
      assertThat(setting.reliability()).isEqualTo(0.5);
    }

    @Test
    void shouldRejectWhenModeIsMissing() {
      // Given / When / Then
      assertThatThrownBy(() -> ChaosSetting.of(null, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("mode");
    }

    @Test
    void shouldRejectWhenSlowFactorBelowOne() {
      // Given / When / Then
      assertThatThrownBy(() -> ChaosSetting.of(ChaosMode.SLOW, 0.5, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("slowFactor");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1})
    void shouldRejectWhenReliabilityOutsideUnitInterval(double reliability) {
      // Given / When / Then
      assertThatThrownBy(() -> ChaosSetting.of(ChaosMode.FLAKY, null, reliability))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("reliability");
    }
  }
}
