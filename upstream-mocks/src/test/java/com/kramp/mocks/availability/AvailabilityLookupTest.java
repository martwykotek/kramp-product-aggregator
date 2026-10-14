package com.kramp.mocks.availability;

import static org.assertj.core.api.Assertions.assertThat;

import com.kramp.mocks.fixtures.Fixtures;
import com.kramp.mocks.fixtures.MockMarket;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AvailabilityLookupTest {

  // Wednesday 2026-10-14 10:00 in Warsaw
  final Clock clock = Clock.fixed(Instant.parse("2026-10-14T08:00:00Z"), ZoneOffset.UTC);
  final AvailabilityLookup lookup =
      new AvailabilityLookup(
          new Fixtures(JsonMapper.builder().build()), new DeliveryDateCalculator(clock));

  @Nested
  class Stock {

    @Test
    void shouldShipFromLocalWarehouseWhenInStockLocally() {
      // Given / When
      AvailabilityResponse response = lookup.stock("W6103", MockMarket.PL_PL).orElseThrow();

      // Then
      assertThat(response.stockLevel()).isEqualTo(42);
      assertThat(response.warehouse().code()).isEqualTo("PL-MOD");
      assertThat(response.warehouse().city()).isEqualTo("Modła Królewska");
      assertThat(response.expectedDelivery()).isEqualTo(LocalDate.parse("2026-10-15"));
    }

    // proves: REQ-MOCK-UPSTREAM-007
    @Test
    void shouldShipFromVarsseveldOneDayLaterWhenLocalWarehouseIsEmpty() {
      // Given / When
      AvailabilityResponse response = lookup.stock("G1564033", MockMarket.PL_PL).orElseThrow();

      // Then
      assertThat(response.stockLevel()).isEqualTo(6);
      assertThat(response.warehouse().code()).isEqualTo("NL-VAR");
      assertThat(response.expectedDelivery()).isEqualTo(LocalDate.parse("2026-10-16"));
    }

    @Test
    void shouldReportRestockDateWhenOutOfStockEverywhere() {
      // Given / When
      AvailabilityResponse response = lookup.stock("W2100860", MockMarket.PL_PL).orElseThrow();

      // Then
      assertThat(response.stockLevel()).isZero();
      assertThat(response.warehouse().code()).isEqualTo("PL-MOD");
      assertThat(response.expectedDelivery()).isEqualTo(LocalDate.parse("2026-10-29"));
    }

    @Test
    void shouldShipFromLocalWarehouseWithoutExtraDayWhenLocalIsCentral() {
      // Given / When
      AvailabilityResponse response = lookup.stock("W6103", MockMarket.NL_NL).orElseThrow();

      // Then
      assertThat(response.warehouse().code()).isEqualTo("NL-VAR");
      assertThat(response.expectedDelivery()).isEqualTo(LocalDate.parse("2026-10-15"));
    }

    @Test
    void shouldBeEmptyWhenProductIsUnknown() {
      // Given / When / Then
      assertThat(lookup.stock("NOPE", MockMarket.PL_PL)).isEmpty();
    }
  }
}
