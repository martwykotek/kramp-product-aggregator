package com.kramp.aggregator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Dependencies point inwards: adapters know the core, the core knows no adapter (AT-NR-01). */
class ArchitectureTest {

  static final JavaClasses PRODUCTION =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.kramp.aggregator");

  static Stream<Arguments> layers() {
    return Stream.of(
        arguments(
            "com.kramp.aggregator.aggregation..",
            new String[] {
              "com.kramp.aggregator.api..",
              "com.kramp.aggregator.source..",
              "com.kramp.aggregator.presentation.."
            }),
        arguments(
            "com.kramp.aggregator.source..",
            new String[] {"com.kramp.aggregator.api..", "com.kramp.aggregator.presentation.."}),
        arguments(
            "com.kramp.aggregator.presentation..", new String[] {"com.kramp.aggregator.api.."}));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("layers")
  void shouldNotDependOnOuterLayersWhenClassesImported(String layer, String[] outerLayers) {
    // Given
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage(layer)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(outerLayers);

    // When / Then
    rule.check(PRODUCTION);
  }
}
