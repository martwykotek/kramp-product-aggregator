package com.kramp.aggregator.observability;

import org.springframework.core.task.TaskDecorator;

/**
 * Carries the caller's MDC (correlation id) into an executor task and restores the worker's own
 * afterwards.
 */
public final class MdcTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable task) {
    throw new UnsupportedOperationException("not implemented");
  }
}
