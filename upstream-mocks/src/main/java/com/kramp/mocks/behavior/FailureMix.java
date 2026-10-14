package com.kramp.mocks.behavior;

/** Relative weights of the failure kinds. */
public record FailureMix(double fastUnavailable, double slowError, double hang) {

  public FailureMix {
    if (fastUnavailable < 0
        || slowError < 0
        || hang < 0
        || fastUnavailable + slowError + hang <= 0) {
      throw new IllegalArgumentException(
          "failure mix weights must be non-negative with a positive sum");
    }
  }

  /** Picks a kind for a uniform random value in [0, 1). */
  FailureKind pick(double uniform) {
    double point = uniform * (fastUnavailable + slowError + hang);
    if (point < fastUnavailable) {
      return FailureKind.FAST_UNAVAILABLE;
    }
    if (point < fastUnavailable + slowError) {
      return FailureKind.SLOW_ERROR;
    }
    return FailureKind.HANG;
  }
}
