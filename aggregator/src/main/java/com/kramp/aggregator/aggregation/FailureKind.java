package com.kramp.aggregator.aggregation;

public enum FailureKind {
  TIMEOUT,
  ERROR,
  CIRCUIT_OPEN,
  /**
   * Still running when a REQUIRED source had already failed the request; not the upstream's fault.
   */
  CANCELLED
}
