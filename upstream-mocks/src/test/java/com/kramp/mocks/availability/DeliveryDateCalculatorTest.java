package com.kramp.mocks.availability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DeliveryDateCalculatorTest {

  @Nested
  class ExpectedDelivery {

    // proves: REQ-MOCK-UPSTREAM-006
    @ParameterizedTest(name = "{0} in {1} +{2} → {3}")
    @CsvSource({
      // Wednesday 10:00 Warsaw, before the 18:00 cutoff → Thursday
      "2026-10-14T08:00:00Z, Europe/Warsaw, 0, 2026-10-15",
      // Wednesday 18:30 Warsaw, after cutoff → Friday
      "2026-10-14T16:30:00Z, Europe/Warsaw, 0, 2026-10-16",
      // Same instant is 17:30 in London, still before cutoff → Thursday
      "2026-10-14T16:30:00Z, Europe/London, 0, 2026-10-15",
      // Friday 17:59 Warsaw → Monday
      "2026-10-16T15:59:00Z, Europe/Warsaw, 0, 2026-10-19",
      // Friday 18:01 Warsaw → Tuesday
      "2026-10-16T16:01:00Z, Europe/Warsaw, 0, 2026-10-20",
      // Saturday → processed Monday → Tuesday
      "2026-10-17T10:00:00Z, Europe/Warsaw, 0, 2026-10-20",
      // Extra working day (central warehouse) → Friday
      "2026-10-14T08:00:00Z, Europe/Warsaw, 1, 2026-10-16"
    })
    void shouldPromiseNextWorkingDayWhenOrderedBeforeCutoff(
        String instant, String zone, int extraDays, String expected) {
      // Given
      DeliveryDateCalculator calculator =
          new DeliveryDateCalculator(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));

      // When
      LocalDate delivery = calculator.expectedDelivery(ZoneId.of(zone), extraDays);

      // Then
      assertThat(delivery).isEqualTo(LocalDate.parse(expected));
    }
  }
}
