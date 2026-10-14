package com.kramp.aggregator.presentation;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The request gets no product view. The detail is written for the client and never carries upstream
 * text or the customer id.
 */
public final class ProductViewException extends RuntimeException {

  private final ErrorCode code;
  private final String detail;
  private final List<String> supportedMarkets;
  private final Optional<Duration> retryAfter;

  ProductViewException(
      ErrorCode code, String detail, List<String> supportedMarkets, Optional<Duration> retryAfter) {
    super(code + ": " + detail);
    this.code = code;
    this.detail = detail;
    this.supportedMarkets = List.copyOf(supportedMarkets);
    this.retryAfter = retryAfter;
  }

  public ErrorCode code() {
    return code;
  }

  public String detail() {
    return detail;
  }

  /** Filled for UNSUPPORTED_MARKET, in configuration order; empty otherwise. */
  public List<String> supportedMarkets() {
    return supportedMarkets;
  }

  /** When asking again may succeed; present for CATALOG_UNAVAILABLE only. */
  public Optional<Duration> retryAfter() {
    return retryAfter;
  }
}
