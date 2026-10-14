package com.kramp.mocks.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.kramp.mocks.fixtures.Fixtures;
import com.kramp.mocks.fixtures.MockMarket;
import java.math.BigDecimal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PricingLookupTest {

  final PricingLookup lookup = new PricingLookup(new Fixtures(JsonMapper.builder().build()));

  @Nested
  class Quote {

    // proves: REQ-MOCK-UPSTREAM-008
    @Test
    void shouldQuoteMarketCurrencyWithCustomerDiscountWhenCustomerKnown() {
      // Given / When
      PriceResponse price = lookup.quote("W6103", MockMarket.PL_PL, "C-1002").orElseThrow();

      // Then
      assertThat(price.currency()).isEqualTo("PLN");
      assertThat(price.basePrice()).isEqualByComparingTo("64.90");
      assertThat(price.discountPercent()).isEqualTo(12);
      assertThat(price.finalPrice()).isEqualByComparingTo(new BigDecimal("57.11"));
      assertThat(price.priceType()).isEqualTo("NET");
    }

    @Test
    void shouldQuoteListPriceWhenNoCustomer() {
      // Given / When
      PriceResponse price = lookup.quote("W6103", MockMarket.EN_GB, null).orElseThrow();

      // Then
      assertThat(price.currency()).isEqualTo("GBP");
      assertThat(price.basePrice()).isEqualByComparingTo("12.95");
      assertThat(price.discountPercent()).isZero();
      assertThat(price.finalPrice()).isEqualByComparingTo("12.95");
    }

    @Test
    void shouldQuoteListPriceWhenCustomerUnknown() {
      // Given / When
      PriceResponse price = lookup.quote("W6103", MockMarket.PL_PL, "C-9999").orElseThrow();

      // Then
      assertThat(price.discountPercent()).isZero();
      assertThat(price.finalPrice()).isEqualByComparingTo("64.90");
    }

    @Test
    void shouldBeEmptyWhenProductIsNotSoldInMarket() {
      // Given / When / Then
      assertThat(lookup.quote("10112PB", MockMarket.PL_PL, null)).isEmpty();
    }
  }
}
