package com.kramp.aggregator.testing;

import java.time.Duration;

/** Timing assertions compare against a budget plus this slack for scheduling jitter on CI. */
public final class Timing {

  public static final Duration SCHEDULING_TOLERANCE = Duration.ofMillis(150);

  private Timing() {}

  public static Duration since(long startNanos) {
    return Duration.ofNanos(System.nanoTime() - startNanos);
  }
}
