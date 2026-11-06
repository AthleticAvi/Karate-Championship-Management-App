package com.management.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.AndFullName.Predicates.fullNameMatching;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

import com.management.enums.PointsType;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.time.LocalDateTime;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * The layer boundaries: gateways, business and data stay apart.
 *
 * <p>Rules the code already passes are strict. Rules it does not yet pass are frozen: recorded
 * violations in {@code config/archunit/frozen} are tolerated and new ones fail the build. See
 * {@code src/main/java/com/management/README.md}.
 */
class ArchitectureTest {

  private static final String[] GATEWAYS = {
    "..controllers..", "..filters..", "..dto..", "..mappers..", "..exceptions.."
  };
  private static final String[] BUSINESS = {"..services..", "..rules.."};
  private static final String[] DOMAIN_AND_BUSINESS = {
    "..services..", "..rules..", "..models..", "..enums.."
  };
  private static final String[] DATA = {"..repositories..", "..models.converters.."};

  private static final String READS_THE_CURRENT_TIME =
      "java\\.time\\..+\\.now\\(\\)"
          + "|java\\.time\\.Clock\\.(system|tick).*"
          + "|java\\.time\\.ZoneId\\.systemDefault\\(\\)"
          + "|java\\.lang\\.System\\.currentTimeMillis\\(\\)";

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages("com.management");

  @Test
  void gateways_neverReachRulesOrTheDataLayer() {
    noClasses()
        .that()
        .resideInAnyPackage(GATEWAYS)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..rules..", "..repositories..")
        .check(CLASSES);
  }

  @Test
  void business_neverDependsOnControllersOrMappers() {
    noClasses()
        .that()
        .resideInAnyPackage(DOMAIN_AND_BUSINESS)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..controllers..", "..filters..", "..mappers..")
        .check(CLASSES);
  }

  @Test
  void business_neverSeesWebTypes() {
    noClasses()
        .that()
        .resideInAnyPackage(DOMAIN_AND_BUSINESS)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework.web..", "org.springframework.http..")
        .check(CLASSES);
  }

  @Test
  void dataLayer_neverDependsOnBusinessOrGateways() {
    noClasses()
        .that()
        .resideInAnyPackage(DATA)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..services..", "..rules..", "..controllers..", "..filters..", "..dto..", "..mappers..")
        .check(CLASSES);
  }

  @Test
  void domainTypes_neverKnowTheApiShapes() {
    noClasses()
        .that()
        .resideInAnyPackage("..models..", "..enums..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..dto..")
        .check(CLASSES);
  }

  @Test
  void services_dependOneWayOnKumiteGameService() {
    noClasses()
        .that()
        .resideInAPackage("..services..")
        .and()
        .doNotHaveSimpleName("KumiteGameService")
        .should()
        .dependOnClassesThat()
        .haveSimpleName("KumiteGameService")
        .check(CLASSES);
  }

  @Test
  void documentationTypes_stayInTheGateways() {
    noClasses()
        .that()
        .resideOutsideOfPackages("..controllers..", "..dto..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("io.swagger..", "org.springdoc..")
        .check(CLASSES);
  }

  @Test
  void localDateTime_isNeverUsed() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .belongToAnyOf(LocalDateTime.class)
        .because("a local time depends on the zone of the instance that read it")
        .check(CLASSES);
  }

  /** {@code System.nanoTime} stays allowed: it measures a duration and never names a time. */
  @Test
  void currentTime_comesOnlyFromTheClockBean() {
    noClasses()
        .that()
        .doNotHaveSimpleName("TimeConfiguration")
        .should()
        .callMethodWhere(target(fullNameMatching(READS_THE_CURRENT_TIME)))
        .orShould()
        .callConstructor(Date.class)
        .because("one injected clock is what keeps match time the same on every instance")
        .check(CLASSES);
  }

  /**
   * Strict rather than frozen, with the one known edge named: a frozen cycle is stored as a single
   * message that counts its dependencies, so any edit to either package would read as a new cycle.
   */
  @Test
  void packages_areFreeOfCyclesBeyondThePointStrategies() {
    slices()
        .matching("com.management.(*)..")
        .should()
        .beFreeOfCycles()
        .ignoreDependency(equivalentTo(PointsType.class), resideInAPackage("..models.strategies.."))
        .check(CLASSES);
  }

  @Test
  void business_doesNotKnowTheApiShapes() {
    noClasses()
        .that()
        .resideInAnyPackage(BUSINESS)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..dto..")
        .check(CLASSES);
  }

  @Test
  void domainTypes_carryNoPersistenceMapping() {
    freeze(
            noClasses()
                .that()
                .resideInAPackage("..models..")
                .and()
                .resideOutsideOfPackage("..models.converters..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework.data.."))
        .check(CLASSES);
  }

  @Test
  void noUtilityPackage() {
    freeze(noClasses().should().resideInAPackage("..util..")).check(CLASSES);
  }
}
