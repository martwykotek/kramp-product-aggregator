package com.kramp.mocks.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class CatalogFixturesTest {

  static final Set<String> RESEARCHED_IDS =
      Set.of(
          "W6103",
          "SH62142",
          "P551329",
          "FFH121GASF",
          "10G1",
          "W2100860",
          "180012130",
          "62052RSC3SKF",
          "VB17800KR",
          "LA10830",
          "G1564033",
          "TS19100KR",
          "RT0502",
          "10112PB",
          "80213500WEKR");

  static final List<String> LANGUAGES = List.of("en", "nl", "de", "fr", "pl");

  static final JsonMapper MAPPER = JsonMapper.builder().build();

  final Fixtures fixtures = new Fixtures(MAPPER);

  static List<String> ids() {
    return RESEARCHED_IDS.stream().sorted().toList();
  }

  /** Reads a fixture file as written, bypassing the lookups {@link Fixtures} offers. */
  static <T> T raw(String file, TypeReference<T> type) throws IOException {
    try (InputStream in = CatalogFixturesTest.class.getResourceAsStream("/fixtures/" + file)) {
      return MAPPER.readValue(in, type);
    }
  }

  @Nested
  class Products {

    // proves: REQ-MOCK-UPSTREAM-005
    @Test
    void shouldContainExactlyTheResearchedKrampProductsWhenLoaded() {
      // Given / When
      Set<String> ids = fixtures.productIds();
      ProductFixture oilFilter = fixtures.product("W6103").orElseThrow();

      // Then
      assertThat(ids).isEqualTo(RESEARCHED_IDS);
      assertThat(oilFilter.content().get("pl").name()).isEqualTo("Filtr oleju Mann & Hummel");
      assertThat(oilFilter.content().get("nl").name()).isEqualTo("Oliefilter M&H");
      assertThat(oilFilter.content().get("de").name()).isEqualTo("Ölfilter M+H");
      assertThat(fixtures.specLabel(oilFilter.specs().getFirst().key(), "pl"))
          .isEqualTo("Gwint montażowy");
      assertThat(fixtures.product("TS19100KR").orElseThrow().content().get("en").name())
          .isEqualTo("Seat top black PVC");
    }

    @ParameterizedTest
    @MethodSource("com.kramp.mocks.fixtures.CatalogFixturesTest#ids")
    void shouldHaveCompleteContentWhenProductIsLoaded(String id) {
      // Given
      ProductFixture product = fixtures.product(id).orElseThrow();

      // When
      Set<String> expectedLanguages =
          switch (id) {
            case "TS19100KR" -> Set.of("en", "de", "fr", "pl");
            case "10112PB" -> Set.of("en", "nl", "de", "fr");
            default -> Set.copyOf(LANGUAGES);
          };

      // Then
      assertThat(product.content().keySet()).isEqualTo(expectedLanguages);
      assertThat(product.content().values())
          .allSatisfy(
              content -> {
                assertThat(content.name()).isNotBlank();
                assertThat(content.category()).isNotBlank();
                assertThat(content.description()).isNotBlank();
                assertThat(content.url()).startsWith("https://www.kramp.com/");
              });
      assertThat(product.images())
          .isNotEmpty()
          .allMatch(url -> url.startsWith("https://images.kramp.com/"));
      assertThat(product.specs())
          .isNotEmpty()
          .allSatisfy(
              spec ->
                  assertThat(LANGUAGES)
                      .allSatisfy(
                          language ->
                              assertThat(fixtures.specLabel(spec.key(), language)).isNotBlank()));
    }

    @Test
    void shouldExcludeProductsMissingFromAShopWhenAssortmentIsChecked() {
      // Given / When / Then
      assertThat(fixtures.product("TS19100KR").orElseThrow().notSoldIn()).containsExactly("nl-NL");
      assertThat(fixtures.product("10112PB").orElseThrow().notSoldIn()).containsExactly("pl-PL");
      assertThat(fixtures.product("W6103").orElseThrow().notSoldIn()).isEmpty();
    }
  }

  @Nested
  class CommercialData {

    @ParameterizedTest
    @MethodSource("com.kramp.mocks.fixtures.CatalogFixturesTest#ids")
    void shouldHavePricesAndStockWhenProductIsLoaded(String id) {
      // Given
      boolean soldInPoland = !id.equals("10112PB");

      // When
      StockFixture stock = fixtures.stock(id).orElseThrow();

      // Then
      assertThat(fixtures.price(id, "EUR")).isPresent();
      assertThat(fixtures.price(id, "GBP")).isPresent();
      assertThat(fixtures.price(id, "PLN").isPresent()).isEqualTo(soldInPoland);
      assertThat(stock.warehouses().keySet())
          .containsExactlyInAnyOrderElementsOf(
              Arrays.stream(Warehouse.values()).map(Warehouse::code).toList());
    }

    @Test
    void shouldKnowSameCustomersInCustomerAndPricingDataWhenLoaded() throws IOException {
      // Given
      Set<String> customerIds =
          raw("customers.json", new TypeReference<List<CustomerFixture>>() {}).stream()
              .map(CustomerFixture::customerId)
              .collect(Collectors.toSet());

      // When
      Set<String> discountedIds =
          raw("discounts.json", new TypeReference<Map<String, Integer>>() {}).keySet();

      // Then
      assertThat(customerIds)
          .isEqualTo(Set.of("C-1001", "C-1002", "C-1003", "C-1004", "C-1005", "C-1006"))
          .isEqualTo(discountedIds);
      assertThat(customerIds).allSatisfy(id -> assertThat(fixtures.discount(id)).isPositive());
      assertThat(fixtures.discount("C-9999")).isZero();
      assertThat(fixtures.customer("C-9999")).isEmpty();
    }
  }
}
