package com.kramp.aggregator.aggregation;

import java.time.Duration;

/** What one source contributed to a request, and how long it took. */
public sealed interface SourceOutcome<T> {

  SourceStatus status();

  Duration duration();

  record Ok<T>(T value, Duration duration) implements SourceOutcome<T> {
    @Override
    public SourceStatus status() {
      return SourceStatus.OK;
    }
  }

  record NotFound<T>(String reason, Duration duration) implements SourceOutcome<T> {
    @Override
    public SourceStatus status() {
      return SourceStatus.NOT_FOUND;
    }
  }

  /** The source does not apply to this request and was never called. */
  record Skipped<T>() implements SourceOutcome<T> {
    @Override
    public SourceStatus status() {
      return SourceStatus.SKIPPED;
    }

    @Override
    public Duration duration() {
      return Duration.ZERO;
    }
  }

  record Failed<T>(FailureKind kind, Duration duration) implements SourceOutcome<T> {
    @Override
    public SourceStatus status() {
      return switch (kind) {
        case TIMEOUT -> SourceStatus.TIMEOUT;
        case ERROR -> SourceStatus.ERROR;
        case CIRCUIT_OPEN -> SourceStatus.CIRCUIT_OPEN;
      };
    }
  }
}
