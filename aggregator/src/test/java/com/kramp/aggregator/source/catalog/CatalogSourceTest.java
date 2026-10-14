package com.kramp.aggregator.source.catalog;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.kramp.aggregator.aggregation.ProductRequest;
import com.kramp.aggregator.aggregation.UpstreamNotFoundException;
import com.kramp.aggregator.aggregation.UpstreamUnavailableException;
import com.kramp.aggregator.testing.TestProperties;
import com.kramp.aggregator.testing.UpstreamClients;
import com.kramp.aggregator.testing.UpstreamStubs;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CatalogSourceTest {

  @RegisterExtension
  static WireMockExtension upstream =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static ProductRequest request(String productId, String market) {
    return new ProductRequest(productId, TestProperties.market(market), Optional.empty());
  }

  CatalogSource source() {
    return new CatalogSource(UpstreamClients.client(upstream.baseUrl(), "catalog"));
  }

  @Nested
  class Request {

    // proves: REQ-AGG-MARKET-003
    @ParameterizedTest
    @CsvSource({"pl-PL, pl", "fr-BE, fr", "de-AT, de", "en-GB, en"})
    void shouldRequestMarketLanguageWhenFetching(String market, String language) {
      // Given
      upstream.stubFor(UpstreamStubs.catalogOk("W6103", language));

      // When
      source().fetch(request("W6103", market));

      // Then
      upstream.verify(
          1,
          getRequestedFor(urlPathEqualTo("/catalog/products/W6103"))
              .withQueryParam("market", equalTo(market))
              .withQueryParam("lang", equalTo(language)));
    }
  }

  @Nested
  class Mapping {

    @Test
    void shouldMapProductWhenCatalogAnswers() {
      // Given
      upstream.stubFor(UpstreamStubs.catalogOk("W6103", "pl"));

      // When
      CatalogProduct product = source().fetch(request("W6103", "pl-PL"));

      // Then
      assertThat(product.id()).isEqualTo("W6103");
      assertThat(product.brand()).isEqualTo("MANN-FILTER");
      assertThat(product.ean()).isEqualTo("4011558738402");
      assertThat(product.salesUnit()).isEqualTo("EA");
      assertThat(product.name()).isEqualTo("Filtr oleju Mann & Hummel");
      assertThat(product.category()).isEqualTo("Filtry wymienne");
      assertThat(product.specs())
          .containsExactly(new CatalogProduct.Spec("Gwint montażowy", "M20 x 1.50 mm (metric)"));
      assertThat(product.images())
          .containsExactly("https://images.kramp.com/images/60/08/w6103.jpg");
      assertThat(product.shopUrl())
          .isEqualTo("https://www.kramp.com/shop-pl/pl/p/filtr-oleju-mann-hummel--W6103");
      assertThat(product.language()).isEqualTo("pl");
    }

    @Test
    void shouldAcceptProductWhenOptionalFieldsAreMissing() {
      // Given — 5 of the 15 researched items have no EAN
      upstream.stubFor(
          get(urlPathMatching(UpstreamStubs.CATALOG))
              .willReturn(
                  okJson(
                      """
                      {"id":"G1564033","brand":"Grammer","name":"Seat Primo XM fabric",\
                      "language":"en"}""")));

      // When
      CatalogProduct product = source().fetch(request("G1564033", "en-GB"));

      // Then
      assertThat(product.id()).isEqualTo("G1564033");
      assertThat(product.ean()).isNull();
      assertThat(product.description()).isNull();
      assertThat(product.category()).isNull();
      assertThat(product.salesUnit()).isNull();
      assertThat(product.shopUrl()).isNull();
      assertThat(product.specs()).isEmpty();
      assertThat(product.images()).isEmpty();
    }
  }

  @Nested
  class NotFound {

    @ParameterizedTest
    @ValueSource(strings = {"PRODUCT_NOT_FOUND", "NOT_IN_ASSORTMENT"})
    void shouldReportNotFoundReasonWhenCatalogAnswers404(String code) {
      // Given
      upstream.stubFor(UpstreamStubs.catalogNotFound(code));

      // When
      Throwable thrown = catchThrowable(() -> source().fetch(request("10112PB", "pl-PL")));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              UpstreamNotFoundException.class,
              notFound -> {
                assertThat(notFound.source()).isEqualTo("catalog");
                assertThat(notFound.reason()).isEqualTo(code);
              });
    }
  }

  @Nested
  class Failures {

    // proves: REQ-AGG-RES-019
    @Test
    void shouldFailAsUnavailableWhenBodyIsEmpty() {
      // Given
      upstream.stubFor(UpstreamStubs.emptyBody(UpstreamStubs.CATALOG));

      // When
      Throwable thrown = catchThrowable(() -> source().fetch(request("W6103", "pl-PL")));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              UpstreamUnavailableException.class,
              unavailable -> {
                assertThat(unavailable.source()).isEqualTo("catalog");
                assertThat(unavailable.retryable()).isFalse();
              });
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "brand", "name", "language"})
    void shouldFailAsUnavailableWhenRequiredFieldMissing(String field) {
      // Given
      List<String> fields =
          List.of(
              "\"id\":\"W6103\"",
              "\"brand\":\"MANN-FILTER\"",
              "\"name\":\"Filtr oleju Mann & Hummel\"",
              "\"language\":\"pl\"");
      String body =
          fields.stream()
              .filter(entry -> !entry.startsWith("\"" + field + "\""))
              .reduce((left, right) -> left + "," + right)
              .map(entries -> "{" + entries + "}")
              .orElseThrow();
      upstream.stubFor(get(urlPathMatching(UpstreamStubs.CATALOG)).willReturn(okJson(body)));

      // When
      Throwable thrown = catchThrowable(() -> source().fetch(request("W6103", "pl-PL")));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              UpstreamUnavailableException.class,
              unavailable -> assertThat(unavailable.retryable()).isFalse());
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "\"specs\":[null]",
          "\"specs\":[{\"label\":\"Gwint montażowy\"}]",
          "\"images\":[null]"
        })
    void shouldFailAsUnavailableWhenListHoldsEmptyEntry(String list) {
      // Given
      String body =
          "{\"id\":\"W6103\",\"brand\":\"MANN-FILTER\",\"name\":\"Filtr oleju Mann & Hummel\","
              + "\"language\":\"pl\","
              + list
              + "}";
      upstream.stubFor(get(urlPathMatching(UpstreamStubs.CATALOG)).willReturn(okJson(body)));

      // When
      Throwable thrown = catchThrowable(() -> source().fetch(request("W6103", "pl-PL")));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              UpstreamUnavailableException.class,
              unavailable -> assertThat(unavailable.retryable()).isFalse());
    }

    @Test
    void shouldTranslateConnectionResetAsRetryableFailure() {
      // Given
      upstream.stubFor(UpstreamStubs.connectionReset(UpstreamStubs.CATALOG));

      // When
      Throwable thrown = catchThrowable(() -> source().fetch(request("W6103", "pl-PL")));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              UpstreamUnavailableException.class,
              unavailable -> assertThat(unavailable.retryable()).isTrue());
    }
  }
}
