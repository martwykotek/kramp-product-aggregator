package com.kramp.aggregator.api.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.kramp.aggregator.testing.SharedWireMock.UPSTREAMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.jsonpath.JsonPath;
import com.kramp.aggregator.testing.SharedWireMock;
import com.kramp.aggregator.testing.Timing;
import com.kramp.aggregator.testing.UpstreamStubs;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// A free port: the gRPC server starts with every context, and 9090 may be taken (docker compose).
@SpringBootTest(properties = "spring.grpc.server.port=0")
@AutoConfigureMockMvc
class ProductRestApiIT {

  static final String PRODUCT = "/api/v1/products/{productId}";

  @Autowired MockMvc mvc;
  @Autowired CircuitBreakerRegistry breakers;

  @DynamicPropertySource
  static void upstreams(DynamicPropertyRegistry registry) {
    SharedWireMock.register(registry);
  }

  @BeforeEach
  void resetUpstreamsAndBreakers() {
    UPSTREAMS.resetAll();
    breakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
  }

  static MockHttpServletRequestBuilder product(String productId, String market) {
    return get(PRODUCT, productId).param("market", market);
  }

  /** W6103 in pl-PL for customer C-1002, the request most cases are about. */
  ResultActions requestStandard() throws Exception {
    return mvc.perform(product("W6103", "pl-PL").header("X-Customer-Id", "C-1002"));
  }

  static Stream<Arguments> optionalFailures() {
    return Stream.of(
        arguments("500", "ERROR"), arguments("503", "ERROR"), arguments("slow 1000 ms", "TIMEOUT"));
  }

  static ResultMatcher allOf(ResultMatcher... matchers) {
    return result -> {
      for (ResultMatcher matcher : matchers) {
        matcher.match(result);
      }
    };
  }

  static MappingBuilder failure(String kind, String path) {
    return switch (kind) {
      case "500" -> UpstreamStubs.serverError(path);
      case "503" -> UpstreamStubs.unavailable(path);
      case "slow 1000 ms" -> UpstreamStubs.slow(path, 1000);
      case "connection reset" -> UpstreamStubs.connectionReset(path);
      default -> throw new IllegalArgumentException("no failure named " + kind);
    };
  }

  @Nested
  class View {

    // proves: REQ-AGG-VIEW-001, REQ-AGG-VIEW-002, REQ-API-REST-001
    @Test
    void shouldReturnAggregatedViewWhenAllUpstreamsAnswer() throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);

      // When / Then
      requestStandard()
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.productId").value("W6103"))
          .andExpect(jsonPath("$.market").value("pl-PL"))
          .andExpect(jsonPath("$.language").value("pl"))
          .andExpect(jsonPath("$.product.name").value("Filtr oleju Mann & Hummel"))
          .andExpect(jsonPath("$.product.brand").value("MANN-FILTER"))
          .andExpect(jsonPath("$.product.specs[0].label").value("Gwint montażowy"))
          .andExpect(jsonPath("$.product.images[0]").value(startsWith("https://images.kramp.com/")))
          // AT-VIEW-02: all four sections
          .andExpect(jsonPath("$.pricing.status").value("AVAILABLE"))
          .andExpect(jsonPath("$.pricing.finalPrice").value(57.11))
          .andExpect(jsonPath("$.availability.status").value("IN_STOCK"))
          .andExpect(jsonPath("$.availability.warehouse.city").value("Modła Królewska"))
          .andExpect(jsonPath("$.personalization.status").value("PERSONALIZED"))
          .andExpect(jsonPath("$.meta.degraded").value(false))
          .andExpect(jsonPath("$.meta.sources.*.status").value(contains("OK", "OK", "OK", "OK")))
          .andExpect(
              jsonPath("$.meta.sources.*.durationMs").value(everyItem(greaterThanOrEqualTo(0))))
          .andExpect(
              result ->
                  assertThat(
                          JsonPath.<Map<String, Object>>read(
                                  result.getResponse().getContentAsString(), "$.meta.sources")
                              .keySet())
                      .containsExactly("catalog", "pricing", "availability", "customer"));
    }

    // proves: REQ-AGG-VIEW-005
    @Test
    void shouldSkipCustomerWhenHeaderMissing() throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);

      // When / Then
      mvc.perform(product("W6103", "pl-PL"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.personalization.status").value("STANDARD"))
          .andExpect(jsonPath("$.personalization.segment").value(nullValue()))
          .andExpect(jsonPath("$.personalization.preferredBrand").value(nullValue()))
          .andExpect(jsonPath("$.meta.sources.customer.status").value("SKIPPED"))
          .andExpect(jsonPath("$.meta.degraded").value(false));
      UPSTREAMS.verify(0, getRequestedFor(urlPathMatching(UpstreamStubs.CUSTOMERS)));
    }

    // proves: REQ-API-REST-002
    @Test
    void shouldPersonalizeWhenCustomerHeaderPresent() throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);

      // When / Then — the customer id is read from the header only
      requestStandard()
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.personalization.status").value("PERSONALIZED"));
      UPSTREAMS.verify(1, getRequestedFor(urlPathEqualTo("/customers/C-1002")));
      mvc.perform(product("W6103", "pl-PL").param("customerId", "C-1002"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.personalization.status").value("STANDARD"));
      UPSTREAMS.verify(1, getRequestedFor(urlPathEqualTo("/customers/C-1002")));
    }
  }

  @Nested
  class Market {

    // proves: REQ-AGG-MARKET-001
    @Test
    void shouldReturn400WhenMarketUnsupported() throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);

      // When / Then
      mvc.perform(product("W6103", "xx-XX"))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.code").value("UNSUPPORTED_MARKET"))
          .andExpect(jsonPath("$.supportedMarkets", hasSize(8)));
      UPSTREAMS.verify(0, anyRequestedFor(anyUrl()));
    }

    // proves: REQ-AGG-MARKET-004
    @Test
    void shouldReportServedLanguageWhenCatalogFallsBack() throws Exception {
      // Given
      UPSTREAMS.stubFor(UpstreamStubs.catalogFallbackEn("TS19100KR"));
      UPSTREAMS.stubFor(UpstreamStubs.pricingOk("EUR", 54.30, 0, 54.30));
      UPSTREAMS.stubFor(
          UpstreamStubs.availabilityOk(3, "NL-VAR", "Varsseveld", "NL", "2026-10-15"));

      // When / Then
      mvc.perform(product("TS19100KR", "nl-BE"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.language").value("en"))
          .andExpect(jsonPath("$.market").value("nl-BE"))
          .andExpect(jsonPath("$.product.name").value("Seat top black PVC"))
          .andExpect(jsonPath("$.availability.label").value(startsWith("Op voorraad")));
    }

    // proves: REQ-AGG-MARKET-007
    @Test
    void shouldReturn404WhenProductOutsideMarketAssortment() throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      UPSTREAMS.stubFor(UpstreamStubs.catalogNotFound("NOT_IN_ASSORTMENT"));

      // When / Then
      mvc.perform(product("10112PB", "pl-PL"))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.code").value("PRODUCT_NOT_IN_MARKET"))
          .andExpect(jsonPath("$.detail").value(containsString("pl-PL")))
          .andExpect(jsonPath("$.pricing").doesNotExist())
          .andExpect(jsonPath("$.availability").doesNotExist());
    }

    // proves: REQ-AGG-MARKET-008
    @Test
    void shouldHidePriceWhenPricingCurrencyDoesNotMatchMarket() throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      UPSTREAMS.stubFor(UpstreamStubs.pricingOk("EUR", 14.90, 12, 13.11));

      // When / Then
      requestStandard()
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.pricing.status").value("UNAVAILABLE"))
          .andExpect(jsonPath("$.pricing.finalPrice").value(nullValue()))
          .andExpect(jsonPath("$.meta.degraded").value(true));
    }
  }

  @Nested
  class CatalogFailure {

    static Stream<String> catalogFailures() {
      return Stream.of("500", "503", "connection reset");
    }

    // proves: REQ-AGG-RES-001
    @ParameterizedTest
    @MethodSource("catalogFailures")
    void shouldReturn503WhenCatalogFails(String kind) throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      UPSTREAMS.stubFor(failure(kind, UpstreamStubs.CATALOG));

      // When / Then
      requestStandard()
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"))
          .andExpect(header().string("Retry-After", "1"));
      UPSTREAMS.verify(2, getRequestedFor(urlPathMatching(UpstreamStubs.CATALOG)));
    }

    // proves: REQ-AGG-RES-002
    @Test
    void shouldReturn504WhenCatalogTooSlow() throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      UPSTREAMS.stubFor(UpstreamStubs.slow(UpstreamStubs.CATALOG, 1000));

      // When
      long start = System.nanoTime();
      ResultActions response = requestStandard();
      Duration elapsed = Timing.since(start);

      // Then
      response
          .andExpect(status().isGatewayTimeout())
          .andExpect(jsonPath("$.code").value("CATALOG_TIMEOUT"));
      assertThat(elapsed).isLessThan(Duration.ofMillis(250).plus(Timing.SCHEDULING_TOLERANCE));
    }

    // proves: REQ-AGG-RES-003
    @Test
    void shouldReturn404WhenProductUnknown() throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      UPSTREAMS.stubFor(UpstreamStubs.catalogNotFound("PRODUCT_NOT_FOUND"));

      // When / Then
      requestStandard()
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
      UPSTREAMS.verify(1, getRequestedFor(urlPathMatching(UpstreamStubs.CATALOG)));
    }

    // proves: REQ-API-REST-005
    @Test
    void shouldAdviseRetryAfterWhenCatalogCircuitOpen() throws Exception {
      // Given — 5 requests × 2 attempts = 10 failures open the catalog breaker
      UpstreamStubs.standard(UPSTREAMS);
      UPSTREAMS.stubFor(UpstreamStubs.serverError(UpstreamStubs.CATALOG));
      for (int i = 0; i < 5; i++) {
        requestStandard().andExpect(status().isServiceUnavailable());
      }

      // When / Then
      requestStandard()
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"))
          .andExpect(header().string("Retry-After", "10"));
      UPSTREAMS.verify(10, getRequestedFor(urlPathMatching(UpstreamStubs.CATALOG)));
    }
  }

  @Nested
  class OptionalFailure {

    // proves: REQ-AGG-RES-005
    @ParameterizedTest(name = "{0}")
    @MethodSource("com.kramp.aggregator.api.rest.ProductRestApiIT#optionalFailures")
    void shouldShowProductWithUnavailablePriceWhenPricingFails(String kind, String sourceStatus)
        throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      UPSTREAMS.stubFor(failure(kind, UpstreamStubs.PRICING));

      // When / Then
      requestStandard()
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.pricing.status").value("UNAVAILABLE"))
          .andExpect(jsonPath("$.pricing.label").value("Cena chwilowo niedostępna"))
          .andExpect(jsonPath("$.pricing.currency").value(nullValue()))
          .andExpect(jsonPath("$.pricing.basePrice").value(nullValue()))
          .andExpect(jsonPath("$.pricing.discountPercent").value(nullValue()))
          .andExpect(jsonPath("$.pricing.finalPrice").value(nullValue()))
          .andExpect(jsonPath("$.pricing.formatted").value(nullValue()))
          .andExpect(jsonPath("$.meta.degraded").value(true))
          .andExpect(jsonPath("$.meta.sources.pricing.status").value(sourceStatus))
          .andExpect(jsonPath("$.product.name").value("Filtr oleju Mann & Hummel"));
    }

    // proves: REQ-AGG-RES-006
    @ParameterizedTest(name = "{0}")
    @MethodSource("com.kramp.aggregator.api.rest.ProductRestApiIT#optionalFailures")
    void shouldShowUnknownStockWhenAvailabilityFails(String kind, String sourceStatus)
        throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      UPSTREAMS.stubFor(failure(kind, UpstreamStubs.AVAILABILITY));

      // When / Then
      requestStandard()
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.availability.status").value("UNKNOWN"))
          .andExpect(jsonPath("$.availability.label").value("Stan magazynowy nieznany"))
          .andExpect(jsonPath("$.meta.degraded").value(true))
          .andExpect(jsonPath("$.meta.sources.availability.status").value(sourceStatus))
          .andExpect(jsonPath("$.pricing.status").value("AVAILABLE"));
    }

    // proves: REQ-AGG-RES-007
    @ParameterizedTest(name = "{0}")
    @MethodSource("com.kramp.aggregator.api.rest.ProductRestApiIT#optionalFailures")
    void shouldFallBackToStandardWhenCustomerFails(String kind, String sourceStatus)
        throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      UPSTREAMS.stubFor(failure(kind, UpstreamStubs.CUSTOMERS));

      // When / Then
      requestStandard()
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.personalization.status").value("STANDARD"))
          .andExpect(jsonPath("$.meta.degraded").value(true))
          .andExpect(
              jsonPath("$.meta.sources.customer.status").value(in(List.of("ERROR", "TIMEOUT"))));
    }

    // proves: REQ-AGG-RES-009
    @Test
    void shouldTreatUnknownCustomerAsStandardWhenCustomerNotFound() throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      UPSTREAMS.stubFor(UpstreamStubs.customerNotFound());

      // When / Then
      mvc.perform(product("W6103", "pl-PL").header("X-Customer-Id", "C-9999"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.personalization.status").value("STANDARD"))
          .andExpect(jsonPath("$.meta.sources.customer.status").value("NOT_FOUND"))
          .andExpect(jsonPath("$.meta.degraded").value(false));
    }

    // proves: REQ-AGG-RES-009
    @Test
    void shouldShowPriceOnRequestWhenPricingHasNoPrice() throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      UPSTREAMS.stubFor(UpstreamStubs.pricingNotFound());

      // When / Then
      requestStandard()
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.pricing.status").value("UNAVAILABLE"))
          .andExpect(jsonPath("$.pricing.label").value("Cena na zapytanie"))
          .andExpect(jsonPath("$.meta.degraded").value(false));
    }

    // proves: RC-AGG-RES-001
    @Test
    void shouldKeepDiscountWhenCustomerFails() throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      UPSTREAMS.stubFor(UpstreamStubs.serverError(UpstreamStubs.CUSTOMERS));

      // When / Then
      requestStandard()
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.personalization.status").value("STANDARD"))
          .andExpect(jsonPath("$.pricing.discountPercent").value(12))
          .andExpect(jsonPath("$.pricing.finalPrice").value(57.11))
          .andExpect(jsonPath("$.meta.degraded").value(true));
    }

    static Stream<Arguments> invalidBodies() {
      ResultMatcher catalogUnavailable =
          allOf(status().isServiceUnavailable(), jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
      ResultMatcher priceHidden =
          allOf(
              status().isOk(),
              jsonPath("$.pricing.status").value("UNAVAILABLE"),
              jsonPath("$.meta.degraded").value(true));
      return Stream.of(
          arguments(
              "catalog empty body",
              UpstreamStubs.emptyBody(UpstreamStubs.CATALOG),
              catalogUnavailable),
          arguments(
              "catalog not JSON",
              UpstreamStubs.malformed(UpstreamStubs.CATALOG),
              catalogUnavailable),
          arguments(
              "pricing without finalPrice",
              WireMock.get(urlPathMatching(UpstreamStubs.PRICING))
                  .willReturn(
                      okJson("{\"currency\":\"PLN\",\"basePrice\":64.90,\"discountPercent\":12}")),
              priceHidden));
    }

    // proves: REQ-AGG-RES-019
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void shouldNeverAnswer500WhenUpstreamBodyIsInvalid(
        String label, MappingBuilder invalid, ResultMatcher expected) throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      UPSTREAMS.stubFor(invalid);

      // When / Then
      requestStandard().andExpect(expected);
    }
  }

  @Nested
  class CircuitBreaking {

    // proves: REQ-AGG-RES-014, RC-AGG-RES-002
    @Test
    void shouldOpenPricingCircuitWhenPricingKeepsTimingOut() throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      UPSTREAMS.stubFor(UpstreamStubs.slow(UpstreamStubs.PRICING, 1000));

      // When / Then — every timeout is recorded, so the 11th request finds the circuit open
      for (int i = 0; i < 10; i++) {
        requestStandard().andExpect(jsonPath("$.meta.sources.pricing.status").value("TIMEOUT"));
      }
      requestStandard().andExpect(jsonPath("$.meta.sources.pricing.status").value("CIRCUIT_OPEN"));
      UPSTREAMS.verify(10, getRequestedFor(urlPathMatching(UpstreamStubs.PRICING)));
    }

    // proves: REQ-AGG-RES-015
    @Test
    void shouldShortCircuitPricingWhenCircuitOpen() throws Exception {
      // Given — 5 requests × 2 attempts = 10 failures open the pricing breaker
      UpstreamStubs.standard(UPSTREAMS);
      UPSTREAMS.stubFor(UpstreamStubs.serverError(UpstreamStubs.PRICING));
      for (int i = 0; i < 5; i++) {
        requestStandard().andExpect(status().isOk());
      }

      // When / Then
      requestStandard()
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.meta.sources.pricing.status").value("CIRCUIT_OPEN"))
          .andExpect(jsonPath("$.meta.sources.pricing.durationMs").value(lessThan(5)));
      UPSTREAMS.verify(10, getRequestedFor(urlPathMatching(UpstreamStubs.PRICING)));
    }
  }

  @Nested
  class Errors {

    static Stream<Arguments> failingRequests() {
      return Stream.of(
          arguments(
              "unsupported market",
              "/api/v1/products/W6103?market=xx-XX",
              null,
              400,
              "UNSUPPORTED_MARKET"),
          arguments(
              "malformed id", "/api/v1/products/W6103$?market=pl-PL", null, 400, "INVALID_REQUEST"),
          arguments("missing market", "/api/v1/products/W6103", null, 400, "INVALID_REQUEST"),
          arguments(
              "unknown product",
              "/api/v1/products/W6103?market=pl-PL",
              UpstreamStubs.catalogNotFound("PRODUCT_NOT_FOUND"),
              404,
              "PRODUCT_NOT_FOUND"),
          arguments(
              "not in market",
              "/api/v1/products/10112PB?market=pl-PL",
              UpstreamStubs.catalogNotFound("NOT_IN_ASSORTMENT"),
              404,
              "PRODUCT_NOT_IN_MARKET"),
          arguments(
              "catalog 500",
              "/api/v1/products/W6103?market=pl-PL",
              UpstreamStubs.serverError(UpstreamStubs.CATALOG),
              503,
              "CATALOG_UNAVAILABLE"),
          arguments(
              "catalog slow",
              "/api/v1/products/W6103?market=pl-PL",
              UpstreamStubs.slow(UpstreamStubs.CATALOG, 1000),
              504,
              "CATALOG_TIMEOUT"),
          arguments("unknown path", "/api/v1/nope", null, 404, "NOT_FOUND"));
    }

    // proves: REQ-API-REST-003
    @ParameterizedTest(name = "{0}")
    @MethodSource("failingRequests")
    void shouldRenderProblemJsonWhenRequestFails(
        String label, String uri, MappingBuilder catalog, int expectedStatus, String code)
        throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      if (catalog != null) {
        UPSTREAMS.stubFor(catalog);
      }

      // When
      MvcResult result = mvc.perform(get(uri)).andReturn();

      // Then
      assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
      assertThat(result.getResponse().getContentType()).isEqualTo("application/problem+json");
      String body = result.getResponse().getContentAsString();
      assertThat(JsonPath.<Integer>read(body, "$.status")).isEqualTo(expectedStatus);
      assertThat(JsonPath.<String>read(body, "$.title")).isNotBlank();
      assertThat(JsonPath.<String>read(body, "$.code")).isEqualTo(code);
      assertThat(JsonPath.<String>read(body, "$.correlationId"))
          .isEqualTo(result.getResponse().getHeader("X-Correlation-Id"));
    }

    static Stream<String> malformedProductIds() {
      return Stream.of("W6103$", "A".repeat(41), "W 6103");
    }

    // proves: REQ-API-REST-004
    @ParameterizedTest
    @MethodSource("malformedProductIds")
    void shouldReturn400WhenProductIdMalformed(String productId) throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);

      // When / Then
      mvc.perform(product(productId, "pl-PL"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
      UPSTREAMS.verify(0, anyRequestedFor(anyUrl()));
    }

    static Stream<String> malformedCustomerIds() {
      return Stream.of("   ", "A".repeat(65), "C-1002&x=1");
    }

    // proves: REQ-API-REST-007
    @ParameterizedTest
    @MethodSource("malformedCustomerIds")
    void shouldRejectWhenCustomerIdIsMalformed(String customerId) throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);

      // When / Then
      mvc.perform(product("W6103", "pl-PL").header("X-Customer-Id", customerId))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
      UPSTREAMS.verify(0, anyRequestedFor(anyUrl()));
    }

    static Stream<Arguments> leakingUpstreams() {
      return Stream.of(
          arguments(UpstreamStubs.CATALOG, 500, "500 Internal Server Error"),
          arguments(UpstreamStubs.PRICING, 500, "500 Internal Server Error"),
          arguments(UpstreamStubs.AVAILABILITY, 503, "503 Service Unavailable"));
    }

    // proves: RC-API-REST-001
    @ParameterizedTest(name = "{0} answers {1}")
    @MethodSource("leakingUpstreams")
    void shouldNeverEchoUpstreamDetailsWhenUpstreamFails(
        String path, int upstreamStatus, String statusLine) throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      UPSTREAMS.stubFor(
          WireMock.get(urlPathMatching(path))
              .willReturn(
                  aResponse()
                      .withStatus(upstreamStatus)
                      .withHeader("X-Upstream-Trace", "abc")
                      .withBody("UPSTREAM-SECRET-42 failed at db-7")));

      // When
      MvcResult result = requestStandard().andReturn();

      // Then
      String body = result.getResponse().getContentAsString();
      assertThat(body).doesNotContain("UPSTREAM-SECRET-42").doesNotContain(statusLine);
      assertThat(result.getResponse().getHeaderNames()).doesNotContain("X-Upstream-Trace");
      for (String name : result.getResponse().getHeaderNames()) {
        assertThat(result.getResponse().getHeaders(name))
            .noneMatch(value -> value.contains("UPSTREAM-SECRET-42") || value.contains(statusLine));
      }
    }
  }

  @Nested
  class Documentation {

    // proves: REQ-API-REST-006
    @Test
    void shouldPublishOpenApiWhenRequested() throws Exception {
      // Given / When / Then
      mvc.perform(get("/v3/api-docs"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.paths['/api/v1/products/{productId}']").exists())
          .andExpect(
              jsonPath("$.paths['/api/v1/products/{productId}'].get.parameters[*].name")
                  .value(hasItems("productId", "market", "X-Customer-Id")))
          .andExpect(
              jsonPath(
                      "$.paths['/api/v1/products/{productId}'].get.parameters[?(@.name == 'X-Customer-Id')].in")
                  .value(contains("header")));
      mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
  }

  @Nested
  class Correlation {

    // proves: REQ-OPS-OBS-002
    @Test
    void shouldForwardCorrelationIdWhenCallingUpstreams() throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);

      // When
      mvc.perform(
              product("W6103", "pl-PL")
                  .header("X-Customer-Id", "C-1002")
                  .header("X-Correlation-Id", "it-corr-1"))
          .andExpect(status().isOk())
          .andExpect(header().string("X-Correlation-Id", "it-corr-1"));

      // Then
      for (String path :
          List.of(
              UpstreamStubs.CATALOG,
              UpstreamStubs.PRICING,
              UpstreamStubs.AVAILABILITY,
              UpstreamStubs.CUSTOMERS)) {
        UPSTREAMS.verify(
            1,
            getRequestedFor(urlPathMatching(path))
                .withHeader("X-Correlation-Id", equalTo("it-corr-1")));
      }
    }
  }

  @Nested
  @ExtendWith(OutputCaptureExtension.class)
  class Logging {

    // proves: RC-OPS-OBS-001
    @Test
    void shouldKeepCustomerIdOutOfLogsWhenRequestFails(CapturedOutput output) throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      UPSTREAMS.stubFor(UpstreamStubs.serverError(UpstreamStubs.PRICING));

      // When
      requestStandard()
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.pricing.status").value("UNAVAILABLE"));

      // Then — the failure and the summary are logged, the customer id is not
      assertThat(output.getAll())
          .contains(
              "upstream_call_failed source=pricing",
              "product_aggregated productId=W6103 market=pl-PL customer=present outcome=DEGRADED")
          .doesNotContain("C-1002");
    }
  }
}
