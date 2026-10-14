package com.kramp.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TraceabilityGateTest {

  // Markers are built by concatenation so this file never carries a real proof marker.
  private static final String MARKER = "//" + " proves: ";

  @TempDir Path repo;

  @BeforeEach
  void setUp() throws IOException {
    Files.createDirectories(repo.resolve("specs/area"));
    Files.createDirectories(repo.resolve(".changes"));
    Files.createDirectories(repo.resolve("module/src/test/java/x"));
    Files.writeString(
        repo.resolve(".changes/CR-001_product-aggregator.md"),
        """
        ## Validation
        | Test Scope | Steps | Expected Outcome | Passed |
        |---|---|---|---|
        | TC-01: something | step | outcome | |
        | TC-11a: something else | step | outcome | |
        """);
  }

  private void leaf(String body) throws IOException {
    Files.writeString(repo.resolve("specs/area/feature.md"), body);
  }

  private void testSource(String markerIds) throws IOException {
    Files.writeString(
        repo.resolve("module/src/test/java/x/SomeTest.java"),
        "class SomeTest {\n  " + MARKER + markerIds + "\n  void t() {}\n}\n");
  }

  private static String leafWith(String bullets, String rows) {
    return "# Feature\n\nOwns: REQ-AREA-FEATURE-*, RC-AREA-FEATURE-*\n\n## What it does\n"
        + bullets
        + "\n## How it's proven\n| ID | Resolved by | Proof | Status |\n|----|-------------|-------|--------|\n"
        + rows;
  }

  @Nested
  class Check {

    @Test
    void shouldPassWhenEveryProvenRequirementHasAMarker() throws IOException {
      // Given
      leaf(
          leafWith(
              "- REQ-AREA-FEATURE-001 — The system shall do one thing.\n",
              "| REQ-AREA-FEATURE-001 | automated | SomeTest | ✅ |\n"));
      testSource("REQ-AREA-FEATURE-001");

      // When
      TraceabilityGate.Result result = new TraceabilityGate(repo, false).check();

      // Then
      assertThat(result.problems()).isEmpty();
      assertThat(result.report()).contains("proof: PASS");
    }

    @Test
    void shouldFailWhenRequirementHasNoRow() throws IOException {
      // Given
      leaf(leafWith("- REQ-AREA-FEATURE-001 — The system shall do one thing.\n", ""));

      // When
      TraceabilityGate.Result result = new TraceabilityGate(repo, false).check();

      // Then
      assertThat(result.problems())
          .extracting(TraceabilityGate.Problem::message)
          .containsExactly("has no row in How it's proven");
    }

    @Test
    void shouldFailWhenAutomatedProofHasNoMarker() throws IOException {
      // Given
      leaf(
          leafWith(
              "- REQ-AREA-FEATURE-001 — The system shall do one thing.\n",
              "| REQ-AREA-FEATURE-001 | automated | SomeTest | ✅ |\n"));

      // When
      TraceabilityGate.Result result = new TraceabilityGate(repo, false).check();

      // Then
      assertThat(result.problems())
          .extracting(TraceabilityGate.Problem::message)
          .containsExactly("claims proof (✅) but no test carries a proves: marker for it");
    }

    @Test
    void shouldPassWhenGapIsHonestAndNotStrict() throws IOException {
      // Given
      leaf(
          leafWith(
              "- REQ-AREA-FEATURE-001 — The system shall do one thing.\n",
              "| REQ-AREA-FEATURE-001 | automated | planned | ❌ no evidence — GAP |\n"));

      // When
      TraceabilityGate.Result result = new TraceabilityGate(repo, false).check();

      // Then
      assertThat(result.problems()).isEmpty();
      assertThat(result.report()).contains("1 gap");
    }

    @Test
    void shouldFailWhenGapRemainsInStrictMode() throws IOException {
      // Given
      leaf(
          leafWith(
              "- REQ-AREA-FEATURE-001 — The system shall do one thing.\n",
              "| REQ-AREA-FEATURE-001 | automated | planned | ❌ no evidence — GAP |\n"));

      // When
      TraceabilityGate.Result result = new TraceabilityGate(repo, true).check();

      // Then
      assertThat(result.problems())
          .extracting(TraceabilityGate.Problem::message)
          .containsExactly("is an open gap (strict mode)");
    }

    @Test
    void shouldFailWhenWarningHasNoLimitation() throws IOException {
      // Given
      leaf(
          leafWith(
              "- REQ-AREA-FEATURE-001 — The system shall do one thing.\n",
              "| REQ-AREA-FEATURE-001 | automated | SomeTest | ⚠️ |\n"));
      testSource("REQ-AREA-FEATURE-001");

      // When
      TraceabilityGate.Result result = new TraceabilityGate(repo, false).check();

      // Then
      assertThat(result.problems())
          .extracting(TraceabilityGate.Problem::message)
          .containsExactly("⚠️ status must name its limitation");
    }

    @Test
    void shouldFailWhenStatusOrEvidenceKindIsUnknown() throws IOException {
      // Given
      leaf(
          leafWith(
              "- REQ-AREA-FEATURE-001 — The system shall do one thing.\n",
              "| REQ-AREA-FEATURE-001 | manual | SomeTest | done |\n"));

      // When
      TraceabilityGate.Result result = new TraceabilityGate(repo, false).check();

      // Then
      assertThat(result.problems())
          .extracting(TraceabilityGate.Problem::message)
          .containsExactly("unknown evidence kind 'manual'", "unknown status 'done'");
    }

    @Test
    void shouldFailWhenIdIsOutsideOwnedNamespace() throws IOException {
      // Given
      leaf(
          leafWith(
              "- REQ-OTHER-AREA-001 — The system shall do one thing.\n",
              "| REQ-OTHER-AREA-001 | automated | planned | ❌ no evidence — GAP |\n"));

      // When
      TraceabilityGate.Result result = new TraceabilityGate(repo, false).check();

      // Then
      assertThat(result.problems())
          .extracting(TraceabilityGate.Problem::message)
          .containsExactly("is outside the leaf's Owns: namespace");
    }

    @Test
    void shouldFailWhenCoveredByTargetIsUnknown() throws IOException {
      // Given
      leaf(
          leafWith(
              "- REQ-AREA-FEATURE-001 — The system shall do one thing.\n",
              "| REQ-AREA-FEATURE-001 | covered-by REQ-AREA-FEATURE-009 | n/a | ✅ |\n"));

      // When
      TraceabilityGate.Result result = new TraceabilityGate(repo, false).check();

      // Then
      assertThat(result.problems())
          .extracting(TraceabilityGate.Problem::message)
          .containsExactly("covered-by points to an unknown or own id: REQ-AREA-FEATURE-009");
    }

    @Test
    void shouldFailWhenMarkerReferencesUndeclaredId() throws IOException {
      // Given
      leaf(
          leafWith(
              "- REQ-AREA-FEATURE-001 — The system shall do one thing.\n",
              "| REQ-AREA-FEATURE-001 | automated | planned | ❌ no evidence — GAP |\n"));
      testSource("REQ-AREA-FEATURE-001, REQ-AREA-FEATURE-777");

      // When
      TraceabilityGate.Result result = new TraceabilityGate(repo, false).check();

      // Then
      assertThat(result.problems())
          .extracting(TraceabilityGate.Problem::id, TraceabilityGate.Problem::message)
          .containsExactly(
              tuple("REQ-AREA-FEATURE-777", "a proves: marker cites an id no leaf declares"));
    }

    @Test
    void shouldFailWhenObservedProofCitesUnknownTestCase() throws IOException {
      // Given
      leaf(
          leafWith(
              "- REQ-AREA-FEATURE-001 — The system shall do one thing.\n",
              "| REQ-AREA-FEATURE-001 | observed | TC-42 | ✅ |\n"));

      // When
      TraceabilityGate.Result result = new TraceabilityGate(repo, false).check();

      // Then
      assertThat(result.problems())
          .extracting(TraceabilityGate.Problem::message)
          .containsExactly("observed proof must cite a TC-nn from CR-001 § Validation");
    }

    @Test
    void shouldPassWhenObservedProofCitesKnownTestCase() throws IOException {
      // Given
      leaf(
          leafWith(
              "- REQ-AREA-FEATURE-001 — The system shall do one thing.\n",
              "| REQ-AREA-FEATURE-001 | observed | TC-01 | ✅ |\n"));

      // When
      TraceabilityGate.Result result = new TraceabilityGate(repo, false).check();

      // Then
      assertThat(result.problems()).isEmpty();
    }

    @Test
    void shouldPassWhenObservedProofCitesLetteredTestCase() throws IOException {
      // Given
      leaf(
          leafWith(
              "- REQ-AREA-FEATURE-001 — The system shall do one thing.\n",
              "| REQ-AREA-FEATURE-001 | observed | TC-11a | ✅ |\n"));

      // When
      TraceabilityGate.Result result = new TraceabilityGate(repo, false).check();

      // Then
      assertThat(result.problems()).isEmpty();
    }
  }
}
