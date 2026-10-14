package com.kramp.aggregator.aggregation;

/** Told about every finished aggregation; lets observability watch without the core knowing it. */
public interface AggregationListener {

  AggregationListener NONE = new AggregationListener() {};

  default void completed(AggregationResult result) {}

  default void failed(RequiredSourceFailedException failure) {}
}
