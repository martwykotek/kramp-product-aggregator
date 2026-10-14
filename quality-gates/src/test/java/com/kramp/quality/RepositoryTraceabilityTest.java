package com.kramp.quality;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RepositoryTraceabilityTest {

  @Nested
  class Check {

    @Test
    void shouldResolveEveryClaimedProofWhenRunOnThisRepository() throws IOException {
      // Given
      Path repoRoot = Path.of("..").toAbsolutePath().normalize();
      boolean strict = Boolean.parseBoolean(System.getProperty("gate.strict", "false"));

      // When
      TraceabilityGate.Result result = new TraceabilityGate(repoRoot, strict).check();

      // Then
      System.out.println(result.report());
      assertThat(result.problems()).as(result.report()).isEmpty();
    }
  }
}
