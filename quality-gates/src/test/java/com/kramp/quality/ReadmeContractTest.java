package com.kramp.quality;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The README answers what the assignment asks for; its quality is judged by a person (TC-18). */
class ReadmeContractTest {

  static final Path README = Path.of("..", "README.md").toAbsolutePath().normalize();
  static final int MIN_NON_EMPTY_LINES = 3;

  @Nested
  class RequiredSections {

    // proves: REQ-DLV-SUBMIT-003, REQ-DLV-SUBMIT-004, REQ-DLV-SUBMIT-005, REQ-DLV-SUBMIT-006
    @ParameterizedTest
    @ValueSource(
        strings = {
          "Quick start",
          "Architecture",
          "Key decisions and trade-offs",
          "How I would operate it",
          "What I would do with more time",
          "Design question A — Related Products",
          "Walkthrough: a customer in Poland",
          "When Pricing starts timing out",
          "Data: what is real and what is fictitious",
          "Use of AI tools"
        })
    void shouldContainRequiredSectionsWhenReadmeChecked(String title) throws IOException {
      // Given
      List<String> readme = Files.readAllLines(README);

      // When
      Optional<List<String>> section = section(readme, title);

      // Then
      assertThat(section).as("a '## %s…' heading in README.md", title).isPresent();
      assertThat(section.get())
          .as("non-empty lines under '## %s…'", title)
          .hasSizeGreaterThanOrEqualTo(MIN_NON_EMPTY_LINES);
    }
  }

  /**
   * The non-empty lines between the first {@code ## } heading that starts with {@code title} and
   * the next {@code ## } heading. Matching by prefix lets "Use of AI tools and my role" satisfy
   * "Use of AI tools" (decision I-17).
   */
  static Optional<List<String>> section(List<String> readme, String title) {
    List<String> body = null;
    for (String line : readme) {
      boolean heading = line.startsWith("## ");
      if (body != null) {
        if (heading) {
          break;
        }
        if (!line.isBlank()) {
          body.add(line);
        }
      } else if (heading && line.substring(3).strip().startsWith(title)) {
        body = new ArrayList<>();
      }
    }
    return Optional.ofNullable(body);
  }
}
