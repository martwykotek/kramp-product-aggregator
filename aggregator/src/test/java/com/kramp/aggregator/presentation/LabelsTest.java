package com.kramp.aggregator.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.kramp.aggregator.testing.TestLabels;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LabelsTest {

  static final String DATE = "15.10.2026";

  /** Test specification 3.4: key → language → text. */
  static final Map<String, Map<String, String>> ORACLE =
      Map.of(
          "pricing.unavailable",
          Map.of(
              "en", "Price temporarily unavailable",
              "nl", "Prijs tijdelijk niet beschikbaar",
              "de", "Preis vorübergehend nicht verfügbar",
              "fr", "Prix temporairement indisponible",
              "pl", "Cena chwilowo niedostępna"),
          "pricing.onRequest",
          Map.of(
              "en", "Price on request",
              "nl", "Prijs op aanvraag",
              "de", "Preis auf Anfrage",
              "fr", "Prix sur demande",
              "pl", "Cena na zapytanie"),
          "availability.inStock",
          Map.of(
              "en", "In stock, delivery {0}",
              "nl", "Op voorraad, levering {0}",
              "de", "Auf Lager, Lieferung {0}",
              "fr", "En stock, livraison {0}",
              "pl", "Dostępny, dostawa {0}"),
          "availability.outOfStock",
          Map.of(
              "en", "Out of stock, expected {0}",
              "nl", "Niet op voorraad, verwacht {0}",
              "de", "Nicht auf Lager, erwartet {0}",
              "fr", "En rupture de stock, prévu le {0}",
              "pl", "Brak w magazynie, przewidywana dostawa {0}"),
          "availability.unknown",
          Map.of(
              "en", "Stock information unavailable",
              "nl", "Voorraadinformatie niet beschikbaar",
              "de", "Lagerbestand nicht verfügbar",
              "fr", "Information de stock indisponible",
              "pl", "Stan magazynowy nieznany"));

  static Stream<Arguments> keysByLanguage() {
    return ORACLE.keySet().stream()
        .sorted()
        .flatMap(
            key ->
                Stream.of("en", "nl", "de", "fr", "pl")
                    .map(language -> arguments(language, key, ORACLE.get(key).get(language))));
  }

  @Nested
  class Text {

    // proves: REQ-AGG-MARKET-006
    @ParameterizedTest(name = "{1} in {0}")
    @MethodSource("com.kramp.aggregator.presentation.LabelsTest#keysByLanguage")
    void shouldTranslateEveryKeyWhenMarketLanguageRequested(
        String language, String key, String expected) {
      // Given
      Labels labels = TestLabels.labels();

      // When
      String text = labels.text(key, Locale.forLanguageTag(language), DATE);

      // Then
      assertThat(text).isEqualTo(expected.replace("{0}", DATE));
      if (!language.equals("en")) {
        assertThat(text).isNotEqualTo(labels.text(key, Locale.ENGLISH, DATE));
      }
    }
  }
}
