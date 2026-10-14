package com.kramp.aggregator.aggregation;

public final class UpstreamTimeoutException extends UpstreamException {

  public UpstreamTimeoutException(String source, String message) {
    super(source, message);
  }
}
