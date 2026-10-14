package com.kramp.aggregator.source.pricing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.kramp.aggregator.aggregation.ProductRequest;
import com.kramp.aggregator.aggregation.UpstreamNotFoundException;
import com.kramp.aggregator.aggregation.UpstreamUnavailableException;
import com.kramp.aggregator.testing.TestProperties;
import com.kramp.aggregator.testing.UpstreamClients;
import com.kramp.aggregator.testing.UpstreamStubs;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class PricingSourceTest {

  @RegisterExtension
  static WireMockExtension upstream =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static ProductRequest request(String market, String customerId) {
    return new ProductRequest(
        "W6103", TestProperties.market(market), Optional.ofNullable(customerId));
  }

  PricingSource source() {
    return new PricingSource(UpstreamClients.client(upstream.baseUrl(), "pricing"));
  }

  Throwable fetchFailure(MappingBuilder stub) {
    upstream.stubFor(stub);
    return catchThrowable(() -> source().fetch(request("pl-PL", "C-1002")));
  }

  @Nested
  class Request {

    // proves: REQ-AGG-VIEW-003
    @Test
    void shouldSendCustomerIdWhenCustomerGiven() {
      // Given
      upstream.stubFor(UpstreamStubs.pricingOk("PLN", 64.90, 12, 57.11));

      // When
      source().fetch(request("pl-PL", "C-1002"));

      // Then
      upstream.verify(
          1,
          getRequestedFor(urlPathEqualTo("/pricing/prices/W6103"))
              .withQueryParam("market", equalTo("pl-PL"))
              .withQueryParam("customerId", equalTo("C-1002")));
    }

    // proves: REQ-AGG-VIEW-003
    @Test
    void shouldOmitCustomerIdWhenNoCustomer() {
      // Given
      upstream.stubFor(UpstreamStubs.pricingOk("PLN", 64.90, 0, 64.90));

      // When
      source().fetch(request("pl-PL", null));

      // Then
      upstream.verify(
          1,
          getRequestedFor(urlPathEqualTo("/pricing/prices/W6103"))
              .withQueryParam("market", equalTo("pl-PL"))
              .withQueryParam("customerId", absent()));
    }
  }

  @Nested
  class Mapping {

    @Test
    void shouldMapQuoteWhenPricingAnswers() {
      // Given
      upstream.stubFor(UpstreamStubs.pricingOk("PLN", 64.90, 12, 57.11));

      // When
      PriceQuote quote = source().fetch(request("pl-PL", "C-1002"));

      // Then
      assertThat(quote)
          .isEqualTo(
              new PriceQuote(
                  Currency.getInstance("PLN"),
                  new BigDecimal("64.90"),
                  12,
                  new BigDecimal("57.11"),
                  "NET"));
    }

    @Test
    void shouldReportNoPriceWhenPricingAnswers404() {
      // Given / When
      Throwable thrown = fetchFailure(UpstreamStubs.pricingNotFound());

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              UpstreamNotFoundException.class,
              notFound -> {
                assertThat(notFound.source()).isEqualTo("pricing");
                assertThat(notFound.reason()).isEqualTo("NO_PRICE");
              });
    }
  }

  @Nested
  class InvalidAnswers {

    static Stream<Arguments> invalidBodies() {
      return Stream.of(
          arguments("empty body", UpstreamStubs.emptyBody(UpstreamStubs.PRICING)),
          arguments(
              "no finalPrice",
              get(urlPathMatching(UpstreamStubs.PRICING))
                  .willReturn(
                      okJson(
                          """
                          {"productId":"W6103","currency":"PLN","basePrice":64.90,\
                          "discountPercent":12,"priceType":"NET"}"""))),
          arguments("not JSON", UpstreamStubs.malformed(UpstreamStubs.PRICING)));
    }

    // proves: REQ-AGG-RES-019
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void shouldFailAsUnavailableWhenRequiredFieldMissing(String label, MappingBuilder stub) {
      // Given / When
      Throwable thrown = fetchFailure(stub);

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              UpstreamUnavailableException.class,
              unavailable -> {
                assertThat(unavailable.source()).isEqualTo("pricing");
                assertThat(unavailable.retryable()).isFalse();
              });
    }

    // proves: REQ-AGG-MARKET-008
    @Test
    void shouldFailAsUnavailableWhenCurrencyDoesNotMatchMarket() {
      // Given / When — a EUR price for the PLN market
      Throwable thrown = fetchFailure(UpstreamStubs.pricingOk("EUR", 14.90, 12, 13.11));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              UpstreamUnavailableException.class,
              unavailable -> assertThat(unavailable.retryable()).isFalse());
    }

    static Stream<Arguments> inconsistentAmounts() {
      return Stream.of(
          arguments("negative price", UpstreamStubs.pricingOk("PLN", -64.90, 0, -64.90)),
          arguments("discount above 100", UpstreamStubs.pricingOk("PLN", 64.90, 120, 0.00)),
          arguments("negative discount", UpstreamStubs.pricingOk("PLN", 64.90, -12, 64.90)),
          arguments("final above base", UpstreamStubs.pricingOk("PLN", 64.90, 12, 72.69)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inconsistentAmounts")
    void shouldFailAsUnavailableWhenAmountsAreInconsistent(String label, MappingBuilder stub) {
      // Given / When
      Throwable thrown = fetchFailure(stub);

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              UpstreamUnavailableException.class,
              unavailable -> assertThat(unavailable.retryable()).isFalse());
    }
  }

  @Nested
  class Failures {

    @Test
    void shouldTranslateServerErrorAsRetryableFailure() {
      // Given / When
      Throwable thrown = fetchFailure(UpstreamStubs.serverError(UpstreamStubs.PRICING));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              UpstreamUnavailableException.class,
              unavailable -> {
                assertThat(unavailable.retryable()).isTrue();
                assertThat(unavailable.getMessage()).isEqualTo("pricing answered 500");
              });
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 429})
    void shouldFailWithoutRetryWhenUpstreamAnswersUnexpectedClientError(int status) {
      // Given / When
      Throwable thrown =
          fetchFailure(
              get(urlPathMatching(UpstreamStubs.PRICING))
                  .willReturn(aResponse().withStatus(status)));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              UpstreamUnavailableException.class,
              unavailable -> assertThat(unavailable.retryable()).isFalse());
    }
  }
}
