package com.kramp.aggregator.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTasksTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Nested
  class Decorate {

    @Test
    void shouldCopyMdcIntoVirtualThreadTaskWhenDecorated() throws InterruptedException {
      // Given
      MDC.put("correlationId", "abc");
      AtomicReference<String> seen = new AtomicReference<>();
      Runnable task = new MdcTaskDecorator().decorate(() -> seen.set(MDC.get("correlationId")));

      // When
      Thread.ofVirtual().start(task).join();

      // Then
      assertThat(seen).hasValue("abc");
    }

    @Test
    void shouldRestorePreviousMdcWhenTaskFinishes() {
      // Given
      MDC.put("correlationId", "abc");
      AtomicReference<String> seen = new AtomicReference<>();
      Runnable task = new MdcTaskDecorator().decorate(() -> seen.set(MDC.get("correlationId")));
      MDC.put("correlationId", "worker-own");

      // When
      task.run();

      // Then
      assertThat(seen).hasValue("abc");
      assertThat(MDC.get("correlationId")).isEqualTo("worker-own");
    }
  }
}
