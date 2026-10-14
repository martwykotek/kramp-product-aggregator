package com.kramp.mocks.behavior;

import java.time.Clock;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class BehaviorConfig {

  private static final Logger log = LoggerFactory.getLogger(BehaviorConfig.class);

  @Bean
  ChaosRegistry chaosRegistry() {
    return new ChaosRegistry();
  }

  @Bean
  UpstreamBehavior upstreamBehavior(BehaviorProperties properties, ChaosRegistry chaos) {
    long seed = properties.seed() != null ? properties.seed() : System.nanoTime();
    log.info("Upstream mock behaviour seed={} (set MOCKS_SEED to reproduce this run)", seed);
    return new UpstreamBehavior(properties, chaos, new Random(seed));
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
