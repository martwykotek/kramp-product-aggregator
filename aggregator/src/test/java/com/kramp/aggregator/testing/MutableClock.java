package com.kramp.aggregator.testing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests advance instead of sleeping; drives the circuit breaker's open-state wait. */
public final class MutableClock extends Clock {

  private volatile Instant now = Instant.parse("2026-10-14T08:00:00Z");

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    return now;
  }

  public void advance(Duration duration) {
    now = now.plus(duration);
  }
}
