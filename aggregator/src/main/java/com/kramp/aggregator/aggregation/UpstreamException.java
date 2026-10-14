package com.kramp.aggregator.aggregation;

/**
 * A source's call did not produce data. Messages are built by our adapters from the source name and
 * status only, never from an HTTP client message, which may carry a URL with a customer id.
 */
public abstract sealed class UpstreamException extends RuntimeException
    permits UpstreamNotFoundException, UpstreamUnavailableException, UpstreamTimeoutException {

  private final String source;

  protected UpstreamException(String source, String message) {
    super(message);
    this.source = source;
  }

  public String source() {
    return source;
  }
}
