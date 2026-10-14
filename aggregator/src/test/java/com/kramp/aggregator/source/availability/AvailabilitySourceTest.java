package com.kramp.aggregator.source.availability;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AvailabilitySourceTest {

  @RegisterExtension
  static WireMockExtension upstream =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static final ProductRequest REQUEST =
      new ProductRequest("W6103", TestProperties.market("pl-PL"), Optional.empty());

  AvailabilitySource source() {
    return new AvailabilitySource(UpstreamClients.client(upstream.baseUrl(), "availability"));
  }

  @Nested
  class Mapping {

    @Test
    void shouldMapStockWhenAvailabilityAnswers() {
      // Given
      upstream.stubFor(
          UpstreamStubs.availabilityOk(42, "PL-MOD", "Modła Królewska", "PL", "2026-10-15"));

      // When
      StockInfo stock = source().fetch(REQUEST);

      // Then
      assertThat(stock)
          .isEqualTo(
              new StockInfo(
                  42,
                  new StockInfo.Warehouse("PL-MOD", "Modła Królewska", "PL"),
                  LocalDate.of(2026, 10, 15)));
      upstream.verify(
          1,
          getRequestedFor(urlPathEqualTo("/availability/stock/W6103"))
              .withQueryParam("market", equalTo("pl-PL")));
    }

    @Test
    void shouldReportNoStockRecordWhenAvailabilityAnswers404() {
      // Given
      upstream.stubFor(
          get(urlPathMatching(UpstreamStubs.AVAILABILITY))
              .willReturn(
                  aResponse()
                      .withStatus(404)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"errorCode\":\"AV-404\",\"detail\":\"unknown product\"}")));

      // When
      Throwable thrown = catchThrowable(() -> source().fetch(REQUEST));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              UpstreamNotFoundException.class,
              notFound -> {
                assertThat(notFound.source()).isEqualTo("availability");
                assertThat(notFound.reason()).isEqualTo("NO_STOCK_RECORD");
              });
    }
  }

  @Nested
  class InvalidAnswers {

    @ParameterizedTest
    @ValueSource(
        strings = {
          """
          {"stockLevel":-1,"warehouse":{"code":"PL-MOD","city":"Modła Królewska","country":"PL"},\
          "expectedDelivery":"2026-10-15"}""",
          """
          {"warehouse":{"code":"PL-MOD","city":"Modła Królewska","country":"PL"},\
          "expectedDelivery":"2026-10-15"}""",
          """
          {"stockLevel":42,"expectedDelivery":"2026-10-15"}""",
          """
          {"stockLevel":42,"warehouse":{"code":"PL-MOD","country":"PL"},\
          "expectedDelivery":"2026-10-15"}""",
          """
          {"stockLevel":42,"warehouse":{"code":"PL-MOD","city":"Modła Królewska","country":"PL"}}"""
        })
    void shouldFailAsUnavailableWhenRequiredFieldMissing(String body) {
      // Given
      upstream.stubFor(get(urlPathMatching(UpstreamStubs.AVAILABILITY)).willReturn(okJson(body)));

      // When
      Throwable thrown = catchThrowable(() -> source().fetch(REQUEST));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              UpstreamUnavailableException.class,
              unavailable -> assertThat(unavailable.retryable()).isFalse());
    }
  }
}
