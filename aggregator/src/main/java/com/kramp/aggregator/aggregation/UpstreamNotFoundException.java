package com.kramp.aggregator.aggregation;

/** The upstream answered that it has no such product, price or customer: a business answer. */
public final class UpstreamNotFoundException extends UpstreamException {

  private final String reason;

  public UpstreamNotFoundException(String source, String reason) {
    super(source, source + " reported not found: " + reason);
    this.reason = reason;
  }

  public String reason() {
    return reason;
  }
}
