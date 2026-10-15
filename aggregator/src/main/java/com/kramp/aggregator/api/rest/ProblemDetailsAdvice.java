package com.kramp.aggregator.api.rest;

import com.kramp.aggregator.observability.CorrelationId;
import com.kramp.aggregator.presentation.ErrorCode;
import com.kramp.aggregator.presentation.ProductViewException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error is RFC 9457 problem+json with our `code` and the `correlationId`. Bodies are built
 * from our error codes only, so no upstream status or text can reach the client.
 */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);
  private static final String UNEXPECTED_FAILURE = "request_failed_unexpectedly";
  private static final String INTERNAL_ERROR = "INTERNAL_ERROR";
  private static final String GENERIC_DETAIL = "The request could not be processed";

  @ExceptionHandler(ProductViewException.class)
  ResponseEntity<ProblemDetail> productView(ProductViewException failure) {
    HttpStatus status = statusOf(failure.code());
    ProblemDetail problem = problem(status, failure.detail(), failure.code().name());
    if (!failure.supportedMarkets().isEmpty()) {
      problem.setProperty("supportedMarkets", failure.supportedMarkets());
    }
    ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
    failure
        .retryAfter()
        .ifPresent(
            retryAfter ->
                response.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter.toSeconds())));
    return response.body(problem);
  }

  /** A bug, not an upstream failure: those never leave the aggregation as exceptions. */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception failure) {
    log.error(UNEXPECTED_FAILURE, failure);
    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    return ResponseEntity.status(status).body(problem(status, GENERIC_DETAIL, INTERNAL_ERROR));
  }

  /**
   * Spring's own problems (unknown path, missing parameter, wrong method) get our fields too. The
   * code set stays closed so clients can switch on it: a client error Spring raises is
   * INVALID_REQUEST, anything else is a failure on our side and is reported like {@link
   * #unexpected}.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception failure,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    boolean clientError = statusCode.is4xxClientError();
    if (!clientError) {
      // Logged before the response is built: Spring builds none once the response is committed.
      log.error(UNEXPECTED_FAILURE, failure);
    }
    ResponseEntity<Object> response =
        super.handleExceptionInternal(failure, body, headers, statusCode, request);
    if (response != null && response.getBody() instanceof ProblemDetail problem) {
      if (!clientError) {
        problem.setDetail(GENERIC_DETAIL);
      }
      problem.setProperty("code", codeOf(statusCode));
      problem.setProperty("correlationId", CorrelationId.current());
    }
    return response;
  }

  private static ProblemDetail problem(HttpStatus status, String detail, String code) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("code", code);
    problem.setProperty("correlationId", CorrelationId.current());
    return problem;
  }

  private static HttpStatus statusOf(ErrorCode code) {
    return switch (code) {
      case INVALID_REQUEST, UNSUPPORTED_MARKET -> HttpStatus.BAD_REQUEST;
      case PRODUCT_NOT_FOUND, PRODUCT_NOT_IN_MARKET -> HttpStatus.NOT_FOUND;
      case CATALOG_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
      case CATALOG_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
    };
  }

  private static String codeOf(HttpStatusCode status) {
    if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
      return "NOT_FOUND";
    }
    return status.is4xxClientError() ? ErrorCode.INVALID_REQUEST.name() : INTERNAL_ERROR;
  }
}
