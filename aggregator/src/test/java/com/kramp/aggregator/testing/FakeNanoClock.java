package com.kramp.aggregator.testing;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** A `System::nanoTime` stand-in that moves only when a test or a fake source advances it. */
public final class FakeNanoClock implements LongSupplier {

  private final AtomicLong now = new AtomicLong();

  @Override
  public long getAsLong() {
    return now.get();
  }

  public void advance(Duration duration) {
    now.addAndGet(duration.toNanos());
  }
}
