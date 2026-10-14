package com.kramp.aggregator.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.kramp.aggregator.aggregation.AggregationResult;
import com.kramp.aggregator.aggregation.FailureKind;
import com.kramp.aggregator.aggregation.ProductRequest;
import com.kramp.aggregator.aggregation.SourceOutcome;
import com.kramp.aggregator.source.availability.StockInfo;
import com.kramp.aggregator.source.catalog.CatalogProduct;
import com.kramp.aggregator.source.customer.CustomerProfile;
import com.kramp.aggregator.testing.Products;
import com.kramp.aggregator.testing.TestLabels;
import com.kramp.aggregator.testing.TestProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProductViewAssemblerTest {

  static final ProductRequest REQUEST =
      new ProductRequest("W6103", TestProperties.market("pl-PL"), Optional.of("C-1002"));

  final ProductViewAssembler assembler = new ProductViewAssembler(TestLabels.labels());

  static Duration ms(long millis) {
    return Duration.ofMillis(millis);
  }

  /** All four sources answered; each test replaces the outcome it is about. */
  static Map<String, SourceOutcome<?>> answered() {
    Map<String, SourceOutcome<?>> outcomes = new LinkedHashMap<>();
    outcomes.put("catalog", new SourceOutcome.Ok<>(Products.W6103_PL, ms(48)));
    outcomes.put("pricing", new SourceOutcome.Ok<>(Products.W6103_PLN_C1002, ms(83)));
    outcomes.put("availability", new SourceOutcome.Ok<>(Products.W6103_PL_MOD, ms(97)));
    outcomes.put("customer", new SourceOutcome.Ok<>(Products.C1002, ms(61)));
    return outcomes;
  }

  ProductView assemble(Map<String, SourceOutcome<?>> outcomes) {
    return assembler.assemble(new AggregationResult(REQUEST, outcomes, false, ms(100)), "corr-1");
  }

  @Nested
  class Product {

    @Test
    void shouldCopyCatalogContentWhenAssembled() {
      // Given
      Map<String, SourceOutcome<?>> outcomes = answered();

      // When
      ProductView view = assemble(outcomes);

      // Then
      assertThat(view.productId()).isEqualTo("W6103");
      assertThat(view.market()).isEqualTo("pl-PL");
      assertThat(view.language()).isEqualTo("pl");
      assertThat(view.product())
          .isEqualTo(
              new ProductView.ProductSection(
                  "Filtr oleju Mann & Hummel",
                  "MANN-FILTER",
                  "Wkręcany filtr oleju MANN-FILTER W 610/3.",
                  "Filtry wymienne",
                  "4011558738402",
                  "EA",
                  List.of(new ProductView.Spec("Gwint montażowy", "M20 x 1.50 mm (metric)")),
                  List.of("https://images.kramp.com/images/60/08/w6103.jpg"),
                  "https://www.kramp.com/shop-pl/pl/p/filtr-oleju-mann-hummel--W6103"));
    }
  }

  @Nested
  class Pricing {

    @Test
    void shouldShowFormattedPriceWhenPricingAnswers() {
      // Given
      Map<String, SourceOutcome<?>> outcomes = answered();

      // When
      ProductView.PricingSection pricing = assemble(outcomes).pricing();

      // Then
      assertThat(pricing.status()).isEqualTo("AVAILABLE");
      assertThat(pricing.currency()).isEqualTo("PLN");
      assertThat(pricing.priceType()).isEqualTo("NET");
      assertThat(pricing.basePrice()).isEqualByComparingTo(new BigDecimal("64.90"));
      assertThat(pricing.discountPercent()).isEqualTo(12);
      assertThat(pricing.finalPrice()).isEqualByComparingTo(new BigDecimal("57.11"));
      assertThat(pricing.formatted().basePrice().replace(' ', ' ')).isEqualTo("64,90 zł");
      assertThat(pricing.formatted().finalPrice().replace(' ', ' ')).isEqualTo("57,11 zł");
      assertThat(pricing.label()).isNull();
    }

    @Test
    void shouldShowPriceOnRequestWhenPricingNotFound() {
      // Given
      Map<String, SourceOutcome<?>> outcomes = answered();
      outcomes.put("pricing", new SourceOutcome.NotFound<>("NO_PRICE", ms(20)));

      // When
      ProductView.PricingSection pricing = assemble(outcomes).pricing();

      // Then
      assertThat(pricing)
          .isEqualTo(
              new ProductView.PricingSection(
                  "UNAVAILABLE", null, null, null, null, null, null, "Cena na zapytanie"));
    }

    @Test
    void shouldShowUnavailablePriceWhenPricingFailed() {
      // Given
      Map<String, SourceOutcome<?>> outcomes = answered();
      outcomes.put("pricing", new SourceOutcome.Failed<>(FailureKind.ERROR, ms(20)));

      // When
      ProductView.PricingSection pricing = assemble(outcomes).pricing();

      // Then
      assertThat(pricing)
          .isEqualTo(
              new ProductView.PricingSection(
                  "UNAVAILABLE", null, null, null, null, null, null, "Cena chwilowo niedostępna"));
    }
  }

  @Nested
  class Availability {

    @Test
    void shouldReportInStockWithDeliveryDateWhenStockAvailable() {
      // Given
      Map<String, SourceOutcome<?>> outcomes = answered();

      // When
      ProductView.AvailabilitySection availability = assemble(outcomes).availability();

      // Then
      assertThat(availability)
          .isEqualTo(
              new ProductView.AvailabilitySection(
                  "IN_STOCK",
                  42,
                  new ProductView.Warehouse("PL-MOD", "Modła Królewska", "PL"),
                  LocalDate.of(2026, 10, 15),
                  "Dostępny, dostawa 15 paź 2026"));
    }

    @Test
    void shouldReportOutOfStockWithRestockDateWhenStockIsZero() {
      // Given
      Map<String, SourceOutcome<?>> outcomes = answered();
      StockInfo empty =
          new StockInfo(
              0,
              new StockInfo.Warehouse("PL-MOD", "Modła Królewska", "PL"),
              LocalDate.of(2026, 10, 28));
      outcomes.put("availability", new SourceOutcome.Ok<>(empty, ms(30)));

      // When
      ProductView.AvailabilitySection availability = assemble(outcomes).availability();

      // Then
      assertThat(availability.status()).isEqualTo("OUT_OF_STOCK");
      assertThat(availability.stockLevel()).isZero();
      assertThat(availability.expectedDelivery()).isEqualTo(LocalDate.of(2026, 10, 28));
      assertThat(availability.label())
          .isEqualTo("Brak w magazynie, przewidywana dostawa 28 paź 2026");
    }

    @Test
    void shouldReportUnknownStockWhenAvailabilityFailed() {
      // Given
      Map<String, SourceOutcome<?>> outcomes = answered();
      outcomes.put("availability", new SourceOutcome.Failed<>(FailureKind.TIMEOUT, ms(350)));

      // When
      ProductView.AvailabilitySection availability = assemble(outcomes).availability();

      // Then
      assertThat(availability)
          .isEqualTo(
              new ProductView.AvailabilitySection(
                  "UNKNOWN", null, null, null, "Stan magazynowy nieznany"));
    }
  }

  @Nested
  class Personalization {

    static Stream<Arguments> brandMatches() {
      return Stream.of(
          arguments("MANN-FILTER", List.of("MANN-FILTER", "SKF"), true),
          arguments("MANN-FILTER", List.of("Facom"), false),
          arguments("Kramp", List.of("Kramp", "Walterscheid"), true),
          arguments("mann-filter", List.of("MANN-FILTER", "SKF"), true));
    }

    // proves: REQ-AGG-VIEW-006
    @ParameterizedTest(name = "{0} in {1} → {2}")
    @MethodSource("brandMatches")
    void shouldFlagPreferredBrandWhenBrandMatches(
        String brand, List<String> preferredBrands, boolean expected) {
      // Given
      Map<String, SourceOutcome<?>> outcomes = answered();
      CatalogProduct product = Products.catalog(brand);
      outcomes.put("catalog", new SourceOutcome.Ok<>(product, ms(48)));
      outcomes.put(
          "customer",
          new SourceOutcome.Ok<>(new CustomerProfile("WORKSHOP", preferredBrands), ms(61)));

      // When
      ProductView.PersonalizationSection personalization = assemble(outcomes).personalization();

      // Then
      assertThat(personalization)
          .isEqualTo(new ProductView.PersonalizationSection("PERSONALIZED", "WORKSHOP", expected));
    }

    @Test
    void shouldShowStandardWhenCustomerSkipped() {
      // Given
      Map<String, SourceOutcome<?>> outcomes = answered();
      outcomes.put("customer", new SourceOutcome.Skipped<>());

      // When
      ProductView.PersonalizationSection personalization = assemble(outcomes).personalization();

      // Then
      assertThat(personalization)
          .isEqualTo(new ProductView.PersonalizationSection("STANDARD", null, null));
    }
  }

  @Nested
  class Meta {

    @Test
    void shouldListSourcesInOrderWhenAssembled() {
      // Given
      Map<String, SourceOutcome<?>> outcomes = answered();
      outcomes.put("pricing", new SourceOutcome.Failed<>(FailureKind.CIRCUIT_OPEN, ms(0)));
      outcomes.put("customer", new SourceOutcome.Skipped<>());

      // When
      ProductView.Meta meta =
          assembler
              .assemble(new AggregationResult(REQUEST, outcomes, true, ms(100)), "corr-1")
              .meta();

      // Then
      assertThat(meta.degraded()).isTrue();
      assertThat(meta.correlationId()).isEqualTo("corr-1");
      assertThat(meta.sources().keySet())
          .containsExactly("catalog", "pricing", "availability", "customer");
      assertThat(meta.sources().values())
          .containsExactly(
              new ProductView.SourceReport("OK", 48),
              new ProductView.SourceReport("CIRCUIT_OPEN", 0),
              new ProductView.SourceReport("OK", 97),
              new ProductView.SourceReport("SKIPPED", 0));
    }
  }
}
