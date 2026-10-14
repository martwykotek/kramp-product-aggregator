package com.kramp.mocks.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kramp.mocks.behavior.ChaosMode;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChaosApiIT {

  /** The `test` profile hangs 50–60 ms, so a 503 after a hang takes at least this long. */
  static final Duration TEST_HANG_MIN = Duration.ofMillis(50);

  /** DOWN must answer well before any hang could end. */
  static final Duration DOWN_ANSWER_LIMIT = Duration.ofMillis(50);

  @Autowired MockMvc mvc;

  @AfterEach
  void resetChaos() throws Exception {
    mvc.perform(delete("/chaos")).andExpect(status().isNoContent());
  }

  private ResultActions putMode(String upstream, String body) throws Exception {
    return mvc.perform(
        put("/chaos/" + upstream).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private static Duration since(long startNanos) {
    return Duration.ofNanos(System.nanoTime() - startNanos);
  }

  @Nested
  class SetMode {

    // proves: REQ-MOCK-CHAOS-001, REQ-MOCK-CHAOS-003
    @ParameterizedTest
    @EnumSource(ChaosMode.class)
    void shouldReportModeWhenModeIsSet(ChaosMode mode) throws Exception {
      // Given
      String body = "{\"mode\":\"" + mode + "\"}";

      // When
      putMode("pricing", body)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.mode").value(mode.name()));

      // Then
      mvc.perform(get("/chaos"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.pricing.mode").value(mode.name()))
          .andExpect(jsonPath("$.catalog.mode").value("HEALTHY"))
          .andExpect(jsonPath("$.availability.mode").value("HEALTHY"))
          .andExpect(jsonPath("$.customer.mode").value("HEALTHY"));
    }

    @Test
    void shouldAcceptSlowFactorWhenModeIsSlow() throws Exception {
      // Given
      String body = "{\"mode\":\"SLOW\",\"slowFactor\":3}";

      // When / Then
      putMode("pricing", body)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.mode").value("SLOW"))
          .andExpect(jsonPath("$.slowFactor").value(3.0));
    }

    @Test
    void shouldRejectWhenUpstreamUnknown() throws Exception {
      // Given
      String body = "{\"mode\":\"DOWN\"}";

      // When / Then
      putMode("warehouse", body).andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectWhenReliabilityOutsideUnitInterval() throws Exception {
      // Given
      String body = "{\"mode\":\"FLAKY\",\"reliability\":2}";

      // When / Then
      putMode("pricing", body).andExpect(status().isBadRequest());
    }
  }

  @Nested
  class Effects {

    @Test
    void shouldAnswer503FromCatalogWhenCatalogIsDown() throws Exception {
      // Given
      putMode("catalog", "{\"mode\":\"DOWN\"}").andExpect(status().isOk());
      long start = System.nanoTime();

      // When
      ResultActions result =
          mvc.perform(get("/catalog/products/W6103").param("market", "pl-PL").param("lang", "pl"));

      // Then
      Duration elapsed = since(start);
      result
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.error.code").value("UNAVAILABLE"));
      assertThat(elapsed).isLessThan(DOWN_ANSWER_LIMIT);
    }

    @Test
    void shouldHangThen503WhenPricingTimesOut() throws Exception {
      // Given
      putMode("pricing", "{\"mode\":\"TIMEOUT\"}").andExpect(status().isOk());
      long start = System.nanoTime();

      // When
      ResultActions result = mvc.perform(get("/pricing/prices/W6103").param("market", "pl-PL"));

      // Then
      Duration elapsed = since(start);
      result.andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503));
      assertThat(elapsed).isGreaterThanOrEqualTo(TEST_HANG_MIN);
    }
  }

  @Nested
  class Cors {

    @ParameterizedTest
    @CsvSource({"/chaos/pricing, PUT", "/chaos, DELETE", "/chaos, GET"})
    void shouldAllowDemoPageOriginWhenPreflighting(String path, String method) throws Exception {
      // Given
      String origin = "http://localhost:8080";

      // When / Then
      mvc.perform(
              options(path)
                  .header("Origin", origin)
                  .header("Access-Control-Request-Method", method))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }

    @Test
    void shouldRefuseOtherOriginWhenPreflighting() throws Exception {
      // Given
      String origin = "http://evil.example";

      // When / Then
      mvc.perform(
              options("/chaos/pricing")
                  .header("Origin", origin)
                  .header("Access-Control-Request-Method", "PUT"))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class Reset {

    // proves: REQ-MOCK-CHAOS-002
    @Test
    void shouldRestoreEveryUpstreamWhenReset() throws Exception {
      // Given
      putMode("catalog", "{\"mode\":\"DOWN\"}").andExpect(status().isOk());
      putMode("availability", "{\"mode\":\"SLOW\",\"slowFactor\":3}").andExpect(status().isOk());

      // When
      mvc.perform(delete("/chaos")).andExpect(status().isNoContent());

      // Then
      mvc.perform(get("/chaos"))
          .andExpect(jsonPath("$.catalog.mode").value("HEALTHY"))
          .andExpect(jsonPath("$.availability.mode").value("HEALTHY"))
          .andExpect(jsonPath("$.pricing.mode").value("HEALTHY"))
          .andExpect(jsonPath("$.customer.mode").value("HEALTHY"));
      mvc.perform(get("/catalog/products/W6103").param("market", "pl-PL").param("lang", "pl"))
          .andExpect(status().isOk());
    }
  }
}
