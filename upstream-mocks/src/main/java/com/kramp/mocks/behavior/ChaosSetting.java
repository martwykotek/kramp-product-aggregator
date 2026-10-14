package com.kramp.mocks.behavior;

/** Current chaos override of one upstream. */
public record ChaosSetting(ChaosMode mode, double slowFactor, double reliability) {

  public static final ChaosSetting HEALTHY = new ChaosSetting(ChaosMode.HEALTHY, 1.0, 1.0);

  static final double DEFAULT_SLOW_FACTOR = 5.0;
  static final double DEFAULT_FLAKY_RELIABILITY = 0.5;

  /** Builds a setting; the API documents slowFactor 5 and reliability 0.5 as defaults. */
  public static ChaosSetting of(ChaosMode mode, Double slowFactor, Double reliability) {
    if (mode == null) {
      throw new IllegalArgumentException("mode is required");
    }
    double factor = slowFactor == null ? DEFAULT_SLOW_FACTOR : slowFactor;
    double flakyReliability = reliability == null ? DEFAULT_FLAKY_RELIABILITY : reliability;
    if (factor < 1.0) {
      throw new IllegalArgumentException("slowFactor must be at least 1");
    }
    if (flakyReliability < 0.0 || flakyReliability > 1.0) {
      throw new IllegalArgumentException("reliability must be between 0 and 1");
    }
    return new ChaosSetting(mode, factor, flakyReliability);
  }
}
