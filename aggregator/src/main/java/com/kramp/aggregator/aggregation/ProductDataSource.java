package com.kramp.aggregator.aggregation;

/** One upstream that contributes a part of the product view. */
public interface ProductDataSource<T> {

  String name();

  Criticality criticality();

  default boolean isApplicable(ProductRequest request) {
    return true;
  }

  /** Throws UpstreamNotFoundException, UpstreamUnavailableException or UpstreamTimeoutException. */
  T fetch(ProductRequest request);
}
