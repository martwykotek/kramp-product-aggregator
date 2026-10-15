package com.kramp.aggregator.source.customer;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
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
import org.junit.jupiter.params.provider.ValueSource;

class CustomerSourceTest {

  @RegisterExtension
  static WireMockExtension upstream =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static ProductRequest request(String customerId) {
    return new ProductRequest(
        "W6103", TestProperties.market("pl-PL"), Optional.ofNullable(customerId));
  }

  CustomerSource source() {
    return new CustomerSource(UpstreamClients.contentClient(upstream.baseUrl()));
  }

  @Nested
  class Applicability {

    @Test
    void shouldApplyOnlyWhenCustomerIdGiven() {
      // Given
      CustomerSource source = source();

      // When / Then
      assertThat(source.isApplicable(request("C-1002"))).isTrue();
      assertThat(source.isApplicable(request(null))).isFalse();
    }
  }

  @Nested
  class Mapping {

    @Test
    void shouldMapProfileWhenCustomerKnown() {
      // Given
      upstream.stubFor(
          UpstreamStubs.customerOk("C-1002", "WORKSHOP", List.of("MANN-FILTER", "SKF")));

      // When
      CustomerProfile profile = source().fetch(request("C-1002"));

      // Then
      assertThat(profile).isEqualTo(new CustomerProfile("WORKSHOP", List.of("MANN-FILTER", "SKF")));
    }

    @Test
    void shouldAcceptProfileWhenPreferredBrandsAreMissing() {
      // Given
      upstream.stubFor(
          get(urlPathMatching(UpstreamStubs.CUSTOMERS))
              .willReturn(okJson("{\"customerId\":\"C-1002\",\"segment\":\"WORKSHOP\"}")));

      // When
      CustomerProfile profile = source().fetch(request("C-1002"));

      // Then
      assertThat(profile).isEqualTo(new CustomerProfile("WORKSHOP", List.of()));
    }

    @Test
    void shouldReportNotFoundWhenCustomerUnknown() {
      // Given
      upstream.stubFor(UpstreamStubs.customerNotFound());

      // When
      Throwable thrown = catchThrowable(() -> source().fetch(request("C-9999")));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              UpstreamNotFoundException.class,
              notFound -> {
                assertThat(notFound.source()).isEqualTo("customer");
                assertThat(notFound.reason()).isEqualTo("UNKNOWN_CUSTOMER");
              });
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "{\"customerId\":\"C-1002\",\"preferredBrands\":[\"SKF\"]}",
          "{\"customerId\":\"C-1002\",\"segment\":\"WORKSHOP\",\"preferredBrands\":[null]}"
        })
    void shouldFailAsUnavailableWhenProfileIsIncomplete(String body) {
      // Given
      upstream.stubFor(get(urlPathMatching(UpstreamStubs.CUSTOMERS)).willReturn(okJson(body)));

      // When
      Throwable thrown = catchThrowable(() -> source().fetch(request("C-1002")));

      // Then
      assertThat(thrown)
          .isInstanceOfSatisfying(
              UpstreamUnavailableException.class,
              unavailable -> assertThat(unavailable.retryable()).isFalse());
    }
  }
}
