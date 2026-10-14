package com.kramp.mocks.behavior;

/** How a simulated upstream fails. */
public enum FailureKind {
  /** Overloaded service or load balancer rejecting quickly: 503 after ~5 ms. */
  FAST_UNAVAILABLE,
  /** A bug in the upstream: 500 after normal latency. */
  SLOW_ERROR,
  /** GC pause or network partition: no answer for seconds, then 503. */
  HANG
}
