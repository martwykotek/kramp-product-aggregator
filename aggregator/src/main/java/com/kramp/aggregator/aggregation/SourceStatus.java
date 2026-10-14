package com.kramp.aggregator.aggregation;

public enum SourceStatus {
  OK,
  SKIPPED,
  NOT_FOUND,
  TIMEOUT,
  ERROR,
  CIRCUIT_OPEN
}
