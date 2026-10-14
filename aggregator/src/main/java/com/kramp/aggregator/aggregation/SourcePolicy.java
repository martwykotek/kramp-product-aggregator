package com.kramp.aggregator.aggregation;

import java.time.Duration;

/**
 * The source's budget for all attempts of one call, and how long a healthy answer usually takes.
 */
public record SourcePolicy(Duration timeout, Duration typicalLatency) {}
