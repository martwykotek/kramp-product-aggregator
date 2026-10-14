package com.kramp.mocks.behavior;

import java.time.Duration;
import java.util.Random;

/**
 * Log-normal latency. Real service latencies are right-skewed: most calls are near the median and a
 * long tail is several times slower (GC, queueing). Sigma is chosen so that p99 is about three
 * times the median: sigma = ln(3) / z(0.99).
 */
public final class LatencyModel {

  static final double SIGMA = Math.log(3) / 2.326;

  private LatencyModel() {}

  public static Duration sample(Duration median, Random random) {
    double factor = Math.exp(SIGMA * random.nextGaussian());
    return Duration.ofNanos((long) (median.toNanos() * factor));
  }
}
