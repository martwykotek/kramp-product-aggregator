package com.kramp.aggregator.observability;

import org.slf4j.MDC;

/** The id that ties a client request to its log lines and upstream calls. */
public final class CorrelationId {

  public static final String MDC_KEY = "correlationId";

  private CorrelationId() {}

  /** The id of the request this thread works for, or null outside a request. */
  public static String current() {
    return MDC.get(MDC_KEY);
  }
}
