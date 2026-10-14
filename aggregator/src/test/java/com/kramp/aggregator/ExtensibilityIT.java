package com.kramp.aggregator;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.kramp.aggregator.testing.SharedWireMock.UPSTREAMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.kramp.aggregator.aggregation.Criticality;
import com.kramp.aggregator.aggregation.ProductDataSource;
import com.kramp.aggregator.aggregation.ProductRequest;
import com.kramp.aggregator.config.AggregatorProperties;
import com.kramp.aggregator.source.UpstreamHttp;
import com.kramp.aggregator.testing.SharedWireMock;
import com.kramp.aggregator.testing.Timing;
import com.kramp.aggregator.testing.UpstreamStubs;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

/**
 * A new upstream joins the aggregation with one bean and one configuration block; no production
 * class changes (REQ-AGG-VIEW-007).
 */
@SpringBootTest(
    properties = {
      "spring.grpc.server.port=0",
      "aggregator.sources.related.timeout=200ms",
      "aggregator.sources.related.typical-latency=100ms"
    })
@AutoConfigureMockMvc
@Import(ExtensibilityIT.RelatedSource.class)
class ExtensibilityIT {

  static final Duration RELATED_TIMEOUT = Duration.ofMillis(200);
  static final String RELATED = "/related/.*";

  @Autowired MockMvc mvc;
  @Autowired CircuitBreakerRegistry breakers;

  @DynamicPropertySource
  static void upstreams(DynamicPropertyRegistry registry) {
    SharedWireMock.register(registry);
    registry.add("aggregator.sources.related.base-url", UPSTREAMS::baseUrl);
  }

  @BeforeEach
  void resetUpstreamsAndBreakers() {
    UPSTREAMS.resetAll();
    breakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
  }

  /** Product ids sold together with the requested one; exists only in this test. */
  @TestConfiguration
  static class RelatedSource {

    @Bean
    ProductDataSource<String> relatedSource(
        AggregatorProperties properties, RestClient.Builder builder) {
      AggregatorProperties.SourceSettings settings = properties.sources().get("related");
      RestClient client = UpstreamHttp.client(builder, settings.baseUrl(), settings.timeout());
      return new ProductDataSource<>() {
        @Override
        public String name() {
          return "related";
        }

        @Override
        public Criticality criticality() {
          return Criticality.OPTIONAL;
        }

        @Override
        public String fetch(ProductRequest request) {
          return UpstreamHttp.fetch(
              "related",
              () ->
                  client
                      .get()
                      .uri("/related/{productId}", request.productId())
                      .retrieve()
                      .body(String.class));
        }
      };
    }
  }

  static Stream<Arguments> relatedAnswers() {
    return Stream.of(
        arguments(
            "answers 200",
            WireMock.get(urlPathMatching(RELATED)).willReturn(okJson("[\"WA6103\"]")),
            "OK",
            false),
        arguments("answers 500", UpstreamStubs.serverError(RELATED), "ERROR", true),
        arguments("answers after 1000 ms", UpstreamStubs.slow(RELATED, 1000), "TIMEOUT", true));
  }

  // proves: REQ-AGG-VIEW-007
  @ParameterizedTest(name = "related {0}")
  @MethodSource("relatedAnswers")
  void shouldAggregateNewSourceWhenRegisteredAsBean(
      String name, MappingBuilder related, String relatedStatus, boolean degraded)
      throws Exception {
    // Given
    UpstreamStubs.standard(UPSTREAMS);
    UPSTREAMS.stubFor(related);

    // When
    long start = System.nanoTime();
    var response =
        mvc.perform(
            get("/api/v1/products/{productId}", "W6103")
                .param("market", "pl-PL")
                .header("X-Customer-Id", "C-1002"));
    Duration elapsed = Timing.since(start);

    // Then
    response
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.meta.sources.related.status").value(relatedStatus))
        .andExpect(
            jsonPath("$.meta.sources.related.durationMs")
                .value(
                    lessThanOrEqualTo(
                        (int) RELATED_TIMEOUT.plus(Timing.SCHEDULING_TOLERANCE).toMillis())))
        .andExpect(jsonPath("$.meta.degraded").value(degraded))
        .andExpect(jsonPath("$.product.name").value("Filtr oleju Mann & Hummel"))
        .andExpect(jsonPath("$.pricing.status").value("AVAILABLE"))
        .andExpect(jsonPath("$.availability.status").value("IN_STOCK"))
        .andExpect(jsonPath("$.personalization.status").value("PERSONALIZED"));
    assertThat(elapsed).isLessThan(RELATED_TIMEOUT.plus(Timing.SCHEDULING_TOLERANCE));
  }
}
