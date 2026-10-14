package com.kramp.mocks.behavior;

import java.time.Duration;

/** What a simulated upstream does with one request: wait, then answer or fail. */
public sealed interface Decision {

  Duration delay();

  record Succeed(Duration delay) implements Decision {}

  record Fail(FailureKind kind, Duration delay) implements Decision {}
}
