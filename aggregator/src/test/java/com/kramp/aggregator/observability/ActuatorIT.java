package com.kramp.aggregator.observability;

import static com.kramp.aggregator.testing.SharedWireMock.UPSTREAMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kramp.aggregator.testing.SharedWireMock;
import com.kramp.aggregator.testing.UpstreamStubs;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// A free port: the gRPC server starts with every context, and 9090 may be taken (docker compose).
@SpringBootTest(properties = "spring.grpc.server.port=0")
@AutoConfigureMockMvc
@AutoConfigureMetrics
class ActuatorIT {

  static final List<String> OPTIONAL_UPSTREAMS =
      List.of(UpstreamStubs.PRICING, UpstreamStubs.AVAILABILITY, UpstreamStubs.CUSTOMERS);

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

  /** W6103 in pl-PL for customer C-1002, so all four upstreams are called. */
  ResultActions requestProduct() throws Exception {
    return mvc.perform(
        get("/api/v1/products/{productId}", "W6103")
            .param("market", "pl-PL")
            .header("X-Customer-Id", "C-1002"));
  }

  @Nested
  class Prometheus {

    // proves: REQ-OPS-OBS-004
    @Test
    void shouldExposeAggregationMetricsWhenScraped() throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      requestProduct().andExpect(status().isOk());

      // When
      String scrape =
          mvc.perform(get("/actuator/prometheus"))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Then
      assertThat(scrape)
          .contains(
              "upstream_call_seconds_count{",
              "aggregation_result_total{",
              "resilience4j_circuitbreaker_state");
    }
  }

  @Nested
  class Probes {

    // proves: REQ-OPS-OBS-005
    @Test
    void shouldStayReadyWhenUpstreamsFail() throws Exception {
      // Given — every upstream answers 503; 15 requests open the breakers. Each request fails with
      // 503, or with 504 when a slow CI runner spends Catalog's budget on the retry: both are
      // failed Catalog calls, which is all this setup needs.
      UPSTREAMS.stubFor(UpstreamStubs.unavailable(UpstreamStubs.CATALOG));
      OPTIONAL_UPSTREAMS.forEach(path -> UPSTREAMS.stubFor(UpstreamStubs.unavailable(path)));
      for (int i = 0; i < 15; i++) {
        requestProduct().andExpect(status().is5xxServerError());
      }
      assertThat(breakers.circuitBreaker("catalog").getState())
          .isEqualTo(CircuitBreaker.State.OPEN);

      // When / Then
      mvc.perform(get("/actuator/health/liveness"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("UP"));
      mvc.perform(get("/actuator/health/readiness"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("UP"));
    }
  }

  @Nested
  class BreakerHealth {

    // proves: REQ-OPS-OBS-006
    @Test
    void shouldReportBreakerStatesWhenHealthQueried() throws Exception {
      // Given — (1) the optional upstreams answer 503: 5 requests × 2 attempts open their breakers
      UpstreamStubs.standard(UPSTREAMS);
      OPTIONAL_UPSTREAMS.forEach(path -> UPSTREAMS.stubFor(UpstreamStubs.unavailable(path)));
      for (int i = 0; i < 5; i++) {
        requestProduct().andExpect(status().isOk());
      }
      // (2) then catalog answers 503 too: 5 more requests open its breaker
      UPSTREAMS.stubFor(UpstreamStubs.unavailable(UpstreamStubs.CATALOG));
      for (int i = 0; i < 5; i++) {
        requestProduct().andExpect(status().isServiceUnavailable());
      }

      // When / Then
      mvc.perform(get("/actuator/health"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.components.upstreams.status").value("UP"))
          .andExpect(jsonPath("$.components.upstreams.details.catalog").value("OPEN"))
          .andExpect(jsonPath("$.components.upstreams.details.pricing").value("OPEN"))
          .andExpect(jsonPath("$.components.upstreams.details.availability").value("OPEN"))
          .andExpect(jsonPath("$.components.upstreams.details.customer").value("OPEN"));

      // Then — fresh breakers report CLOSED
      breakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
      mvc.perform(get("/actuator/health"))
          .andExpect(jsonPath("$.components.upstreams.details.catalog").value("CLOSED"))
          .andExpect(jsonPath("$.components.upstreams.details.pricing").value("CLOSED"));
    }
  }
}
