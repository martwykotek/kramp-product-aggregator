package com.kramp.aggregator.observability;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Carries the caller's MDC (correlation id) into an executor task and restores the worker's own
 * afterwards.
 */
public final class MdcTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable task) {
    Map<String, String> callerContext = MDC.getCopyOfContextMap();
    return () -> {
      Map<String, String> workerContext = MDC.getCopyOfContextMap();
      apply(callerContext);
      try {
        task.run();
      } finally {
        apply(workerContext);
      }
    };
  }

  private static void apply(Map<String, String> context) {
    if (context == null) {
      MDC.clear();
    } else {
      MDC.setContextMap(context);
    }
  }
}
