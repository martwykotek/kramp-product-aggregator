package com.kramp.aggregator.market;

import java.util.List;

/** The requested market code is not one of the configured markets. */
public final class UnsupportedMarketException extends RuntimeException {

  private final String requested;
  private final List<String> supported;

  public UnsupportedMarketException(String requested, List<String> supported) {
    super("market '" + requested + "' is not supported");
    this.requested = requested;
    this.supported = List.copyOf(supported);
  }

  public String requested() {
    return requested;
  }

  public List<String> supported() {
    return supported;
  }
}
