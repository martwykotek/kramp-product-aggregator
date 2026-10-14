package com.kramp.aggregator.aggregation;

/** The upstream failed or answered something unusable. */
public final class UpstreamUnavailableException extends UpstreamException {

  private final boolean retryable;

  public UpstreamUnavailableException(String source, String message, boolean retryable) {
    super(source, message);
    this.retryable = retryable;
  }

  /** True for fast failures that a second attempt may cure: an upstream that answered 5xx. */
  public boolean retryable() {
    return retryable;
  }
}
