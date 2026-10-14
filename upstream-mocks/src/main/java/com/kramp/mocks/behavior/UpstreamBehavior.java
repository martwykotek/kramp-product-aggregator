package com.kramp.mocks.behavior;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

/** Decides how long a simulated upstream waits and whether it fails. */
public class UpstreamBehavior {

  static final Duration FAST_FAILURE_DELAY = Duration.ofMillis(5);

  private final BehaviorProperties properties;
  private final ChaosRegistry chaos;
  private final Random random;
  private final ReentrantLock lock = new ReentrantLock();

  public UpstreamBehavior(BehaviorProperties properties, ChaosRegistry chaos, Random random) {
    this.properties = properties;
    this.chaos = chaos;
    this.random = random;
  }

  public Decision decide(Upstream upstream) {
    BehaviorProperties.Profile profile = properties.upstreams().get(upstream);
    ChaosSetting setting = chaos.get(upstream);
    // One lock keeps the random sequence reproducible for a given seed under concurrent requests.
    lock.lock();
    try {
      return switch (setting.mode()) {
        case HEALTHY -> regular(profile.medianLatency(), profile.reliability());
        case SLOW ->
            regular(
                Duration.ofNanos((long) (profile.medianLatency().toNanos() * setting.slowFactor())),
                profile.reliability());
        case FLAKY -> regular(profile.medianLatency(), setting.reliability());
        case TIMEOUT -> new Decision.Fail(FailureKind.HANG, hang());
        case DOWN -> new Decision.Fail(FailureKind.FAST_UNAVAILABLE, FAST_FAILURE_DELAY);
      };
    } finally {
      lock.unlock();
    }
  }

  private Decision regular(Duration median, double reliability) {
    if (random.nextDouble() >= reliability) {
      FailureKind kind = properties.failureMix().pick(random.nextDouble());
      Duration delay =
          switch (kind) {
            case FAST_UNAVAILABLE -> FAST_FAILURE_DELAY;
            case SLOW_ERROR -> LatencyModel.sample(median, random);
            case HANG -> hang();
          };
      return new Decision.Fail(kind, delay);
    }
    return new Decision.Succeed(LatencyModel.sample(median, random));
  }

  private Duration hang() {
    long min = properties.hangMin().toMillis();
    long max = properties.hangMax().toMillis();
    return Duration.ofMillis(min + (long) (random.nextDouble() * (max - min)));
  }
}
