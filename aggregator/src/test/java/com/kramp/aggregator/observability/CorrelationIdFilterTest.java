package com.kramp.aggregator.observability;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  final CorrelationIdFilter filter = new CorrelationIdFilter();
  final AtomicReference<String> seenByChain = new AtomicReference<>();
  final FilterChain chain = (request, response) -> seenByChain.set(MDC.get("correlationId"));

  MockHttpServletResponse filter(String header) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products/W6103");
    if (header != null) {
      request.addHeader("X-Correlation-Id", header);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, chain);
    return response;
  }

  @Nested
  class Accept {

    // proves: REQ-OPS-OBS-001
    @Test
    void shouldEchoWhenHeaderValid() throws Exception {
      // Given
      String valid = "it-corr_1.A";

      // When
      MockHttpServletResponse response = filter(valid);

      // Then
      assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(valid);
      assertThat(seenByChain.get()).isEqualTo(valid);
      assertThat(MDC.get("correlationId")).isNull();
    }

    static Stream<String> invalidHeaders() {
      return Stream.of("A".repeat(65), "has space", "line\nbreak");
    }

    // proves: REQ-OPS-OBS-001
    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("invalidHeaders")
    void shouldGenerateWhenHeaderMissingOrInvalid(String header) throws Exception {
      // Given / When
      MockHttpServletResponse response = filter(header);

      // Then
      String echoed = response.getHeader("X-Correlation-Id");
      assertThat(echoed).isNotEqualTo(header);
      assertThat(UUID.fromString(echoed).toString()).isEqualTo(echoed);
      assertThat(seenByChain.get()).isEqualTo(echoed);
      assertThat(MDC.get("correlationId")).isNull();
    }
  }
}
