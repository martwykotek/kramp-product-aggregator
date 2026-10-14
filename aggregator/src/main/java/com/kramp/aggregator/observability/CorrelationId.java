package com.kramp.aggregator.observability;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/** The id that ties a client request to its log lines and upstream calls. */
public final class CorrelationId {

  public static final String HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "correlationId";

  /** Anything else could inject text into headers and log lines. */
  private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  private CorrelationId() {}

  /** The client's id when it is safe to repeat, otherwise a new one. */
  public static String acceptOrGenerate(String candidate) {
    return candidate != null && ACCEPTED.matcher(candidate).matches()
        ? candidate
        : UUID.randomUUID().toString();
  }

  /** The id of the request this thread works for, or null outside a request. */
  public static String current() {
    return MDC.get(MDC_KEY);
  }
}
