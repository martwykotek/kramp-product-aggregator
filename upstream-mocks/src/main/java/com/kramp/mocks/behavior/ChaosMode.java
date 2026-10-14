package com.kramp.mocks.behavior;

/** Demo overrides of an upstream's behaviour. */
public enum ChaosMode {
  HEALTHY,
  SLOW,
  TIMEOUT,
  DOWN,
  FLAKY
}
