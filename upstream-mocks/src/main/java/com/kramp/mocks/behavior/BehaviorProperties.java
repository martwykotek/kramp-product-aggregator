package com.kramp.mocks.behavior;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Latency and reliability per upstream (values from the assignment) plus the failure mix. {@code
 * seed} is optional: without it every start uses a fresh seed, which is logged.
 */
@ConfigurationProperties("mocks")
public record BehaviorProperties(
    Long seed,
    Duration hangMin,
    Duration hangMax,
    FailureMix failureMix,
    Map<Upstream, Profile> upstreams) {

  public record Profile(Duration medianLatency, double reliability) {}

  public BehaviorProperties {
    if (hangMin == null || hangMax == null || hangMin.compareTo(hangMax) > 0) {
      throw new IllegalArgumentException(
          "mocks.hang-min must be set and not exceed mocks.hang-max");
    }
    if (failureMix == null) {
      throw new IllegalArgumentException("mocks.failure-mix is required");
    }
    if (upstreams == null || upstreams.size() != Upstream.values().length) {
      throw new IllegalArgumentException("mocks.upstreams must configure every upstream");
    }
    upstreams.forEach(
        (upstream, profile) -> {
          if (profile.medianLatency() == null || profile.medianLatency().isNegative()) {
            throw new IllegalArgumentException(upstream.id() + " median-latency must be set");
          }
          if (profile.reliability() < 0.0 || profile.reliability() > 1.0) {
            throw new IllegalArgumentException(upstream.id() + " reliability must be in [0, 1]");
          }
        });
  }
}
