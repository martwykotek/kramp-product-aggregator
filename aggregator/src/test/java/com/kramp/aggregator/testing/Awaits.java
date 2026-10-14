package com.kramp.aggregator.testing;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/** Polls for something another thread does, with a deadline instead of a fixed sleep. */
public final class Awaits {

  private Awaits() {}

  public static void until(BooleanSupplier condition, Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("condition not met within " + timeout);
      }
      LockSupport.parkNanos(Duration.ofMillis(5).toNanos());
    }
  }
}
