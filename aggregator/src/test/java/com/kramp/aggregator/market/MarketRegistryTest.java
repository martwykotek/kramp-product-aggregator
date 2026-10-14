package com.kramp.aggregator.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.kramp.aggregator.testing.TestProperties;
import java.util.Currency;
import java.util.Locale;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MarketRegistryTest {

  @Nested
  class Resolve {

    // proves: REQ-AGG-MARKET-001
    @Test
    void shouldRejectWhenMarketNotConfigured() {
      // Given
      MarketRegistry registry = new MarketRegistry(TestProperties.markets());

      // When
      Throwable thrown = catchThrowable(() -> registry.resolve("xx-XX"));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              UnsupportedMarketException.class,
              unsupported -> {
                assertThat(unsupported.requested()).isEqualTo("xx-XX");
                assertThat(unsupported.supported())
                    .containsExactly(
                        "nl-NL", "nl-BE", "fr-BE", "fr-FR", "de-DE", "de-AT", "pl-PL", "en-GB");
              });
    }

    // proves: REQ-AGG-MARKET-002
    @ParameterizedTest
    @ValueSource(strings = {"pl-PL", "PL-pl", "pl-pl", "PL_PL", " pl-PL "})
    void shouldNormalizeWhenCodeHasOddCasingOrSeparator(String requested) {
      // Given
      MarketRegistry registry = new MarketRegistry(TestProperties.markets());

      // When
      Market market = registry.resolve(requested);

      // Then
      assertThat(market.code()).isEqualTo("pl-PL");
      assertThat(market.locale()).isEqualTo(Locale.of("pl", "PL"));
      assertThat(market.currency()).isEqualTo(Currency.getInstance("PLN"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"pl", "pl-PL-x"})
    void shouldRejectWhenCodeIsNotLanguageAndCountry(String requested) {
      // Given
      MarketRegistry registry = new MarketRegistry(TestProperties.markets());

      // When
      Throwable thrown = catchThrowable(() -> registry.resolve(requested));

      // Then
      assertThat(thrown).isExactlyInstanceOf(UnsupportedMarketException.class);
    }
  }
}
