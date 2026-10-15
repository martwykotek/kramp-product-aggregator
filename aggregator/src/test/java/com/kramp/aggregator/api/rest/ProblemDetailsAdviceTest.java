package com.kramp.aggregator.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;

class ProblemDetailsAdviceTest {

  final ProblemDetailsAdvice advice = new ProblemDetailsAdvice();

  @Nested
  @ExtendWith(OutputCaptureExtension.class)
  class SpringRaisedError {

    // proves: REQ-API-REST-003
    @Test
    void shouldAnswerInternalErrorWhenSpringRaisesServerError(CapturedOutput output)
        throws Exception {
      // Given — Spring raises this when the response body cannot be serialised
      HttpMessageNotWritableException failure =
          new HttpMessageNotWritableException("Could not write JSON: field pricing");
      ServletWebRequest request =
          new ServletWebRequest(
              new MockHttpServletRequest("GET", "/api/v1/products/W6103"),
              new MockHttpServletResponse());

      // When
      ResponseEntity<Object> response = advice.handleException(failure, request);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody())
          .isInstanceOfSatisfying(
              ProblemDetail.class,
              problem -> {
                assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
                assertThat(problem.getDetail()).isEqualTo("The request could not be processed");
              });
      assertThat(
              output
                  .getAll()
                  .lines()
                  .filter(line -> line.contains("request_failed_unexpectedly"))
                  .toList())
          .hasSize(1);
    }
  }
}
