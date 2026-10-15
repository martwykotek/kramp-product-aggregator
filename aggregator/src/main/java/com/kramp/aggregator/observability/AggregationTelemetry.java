package com.kramp.aggregator.observability;

import com.kramp.aggregator.aggregation.AggregationListener;
import com.kramp.aggregator.aggregation.AggregationResult;
import com.kramp.aggregator.aggregation.ProductRequest;
import com.kramp.aggregator.aggregation.RequiredSourceFailedException;
import com.kramp.aggregator.aggregation.SourceOutcome;
import com.kramp.aggregator.aggregation.SourceStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One summary log line and the metrics for every aggregation. The customer is logged only as
 * present or absent: its id is personal data (RC-OPS-OBS-001).
 */
public final class AggregationTelemetry implements AggregationListener {

  private static final Logger log = LoggerFactory.getLogger(AggregationTelemetry.class);

  private final MeterRegistry meters;

  public AggregationTelemetry(MeterRegistry meters) {
    this.meters = meters;
  }

  @Override
  public void completed(AggregationResult result) {
    record(
        result.request(),
        result.degraded() ? "DEGRADED" : "OK",
        result.duration(),
        result.outcomes());
  }

  @Override
  public void failed(RequiredSourceFailedException failure) {
    record(failure.request(), "FAILED", failure.duration(), failure.outcomes());
  }

  private void record(
      ProductRequest request,
      String outcome,
      Duration duration,
      Map<String, SourceOutcome<?>> outcomes) {
    StringBuilder line =
        new StringBuilder("product_aggregated productId=")
            .append(request.productId())
            .append(" market=")
            .append(request.market().code())
            .append(" customer=")
            .append(request.customerId().isPresent() ? "present" : "absent")
            .append(" outcome=")
            .append(outcome)
            .append(" durationMs=")
            .append(duration.toMillis());
    outcomes.forEach(
        (source, sourceOutcome) -> {
          line.append(' ')
              .append(source)
              .append('=')
              .append(sourceOutcome.status())
              .append('/')
              .append(sourceOutcome.duration().toMillis());
          // Neither a skipped nor a cancelled call says anything about the upstream.
          if (sourceOutcome.status() != SourceStatus.SKIPPED
              && sourceOutcome.status() != SourceStatus.CANCELLED) {
            Timer.builder("upstream.call")
                .tag("source", source)
                .tag("outcome", sourceOutcome.status().name())
                .register(meters)
                .record(sourceOutcome.duration());
          }
        });
    Counter.builder("aggregation.result").tag("outcome", outcome).register(meters).increment();
    log.info(line.toString());
  }
}
