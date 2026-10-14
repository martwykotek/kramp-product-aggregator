package com.kramp.aggregator.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PriceFormatterTest {

  /** JDK locale data uses no-break spaces; the oracle is written with plain ones. */
  static String normaliseSpaces(String formatted) {
    return formatted.replace(' ', ' ').replace(' ', ' ');
  }

  @Nested
  class Format {

    // proves: REQ-AGG-MARKET-005
    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = {
          "57.11 | PLN | pl-PL | 57,11 zł",
          "14.90 | EUR | de-AT | € 14,90",
          "12.95 | GBP | en-GB | £12.95",
          "54.30 | EUR | fr-BE | 54,30 €",
          "14.90 | EUR | nl-NL | € 14,90"
        })
    void shouldFormatForMarketLocaleWhenFormatting(
        String amount, String currency, String market, String expected) {
      // Given
      Locale locale = Locale.forLanguageTag(market);

      // When
      String formatted =
          PriceFormatter.format(new BigDecimal(amount), Currency.getInstance(currency), locale);

      // Then
      assertThat(normaliseSpaces(formatted)).isEqualTo(expected);
    }
  }
}
