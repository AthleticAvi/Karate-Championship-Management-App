package com.management.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.properties.HasName.AndFullName.Predicates.fullNameMatching;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

import com.management.kumitegame.data.converters.LegacyWinnerConverter;
import com.management.kumitegame.domain.KumiteGame;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The layer boundaries: gateways, business and data stay apart, inside every feature.
 *
 * <p>Rules the code already passes are strict. Rules it does not yet pass are frozen: recorded
 * violations in {@code config/archunit/frozen} are tolerated and new ones fail the build. See
 * {@code src/main/java/com/management/README.md}.
 */
class ArchitectureTest {

  private static final String ROOT = "com.management";
  private static final List<String> FEATURES = List.of("kumitegame", "athlete", "account");

  // Rooted, because Spring has packages named web, data, domain and service too.
  private static final String WEB = ROOT + "..web..";
  private static final String SERVICE = ROOT + "..service..";
  private static final String RULES = ROOT + "..rules..";
  private static final String DOMAIN = ROOT + "..domain..";
  private static final String DATA = ROOT + "..data..";

  private static final String[] GATEWAYS = {WEB, "..filters..", "..security.."};
  private static final String[] BUSINESS = {SERVICE, RULES};
  private static final String[] DOMAIN_AND_BUSINESS = {SERVICE, RULES, DOMAIN};

  private static final String READS_THE_CURRENT_TIME =
      "java\\.time\\..+\\.now\\(\\)"
          + "|java\\.time\\.Clock\\.(system|tick).*"
          + "|java\\.time\\.ZoneId\\.systemDefault\\(\\)"
          + "|java\\.lang\\.System\\.currentTimeMillis\\(\\)";

  /**
   * One slice per layer, across the features, and one per shared package: the slices the layer
   * packages were before the code moved into features.
   */
  private static final SliceAssignment LAYERS =
      new SliceAssignment() {
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
          List<String> path = List.of(javaClass.getPackageName().split("\\."));
          int layer = path.size() > 2 && FEATURES.contains(path.get(2)) ? 3 : 2;
          return path.size() > layer
              ? SliceIdentifier.of(path.get(layer))
              : SliceIdentifier.ignore();
        }

        @Override
        public String getDescription() {
          return "layers";
        }
      };

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages(ROOT);

  @Test
  void gateways_neverReachRulesOrTheDataLayer() {
    noClasses()
        .that()
        .resideInAnyPackage(GATEWAYS)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(RULES, DATA)
        .check(CLASSES);
  }

  @Test
  void business_neverDependsOnTheGateways() {
    noClasses()
        .that()
        .resideInAnyPackage(DOMAIN_AND_BUSINESS)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(GATEWAYS)
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
        .resideInAPackage(DATA)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(SERVICE, RULES, WEB, "..filters..", "..security..")
        .check(CLASSES);
  }

  @Test
  void domainTypes_neverKnowTheApiShapes() {
    noClasses()
        .that()
        .resideInAPackage(DOMAIN)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(WEB)
        .check(CLASSES);
  }

  @Test
  void services_dependOneWayOnKumiteGameService() {
    noClasses()
        .that()
        .resideInAPackage(SERVICE)
        .and()
        .doNotHaveSimpleName("KumiteGameService")
        .should()
        .dependOnClassesThat()
        .haveSimpleName("KumiteGameService")
        .check(CLASSES);
  }

  /** {@code kumitegame} may use {@code athlete}. No other feature uses another. */
  @Test
  void features_dependOnlyKumiteGameOnAthlete() {
    CompositeArchRule.of(featureUsesNoOtherFeature("kumitegame", "athlete"))
        .and(featureUsesNoOtherFeature("athlete"))
        .and(featureUsesNoOtherFeature("account"))
        .check(CLASSES);
  }

  @Test
  void documentationTypes_stayInTheGateways() {
    noClasses()
        .that()
        .resideOutsideOfPackage(WEB)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("io.swagger..", "org.springdoc..")
        .check(CLASSES);
  }

  /** The exception handler turns a security failure into an answer, so the web layer may too. */
  @Test
  void securityTypes_stayInTheGateways() {
    noClasses()
        .that()
        .resideOutsideOfPackages("..security..", WEB)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("org.springframework.security..")
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
   * message that counts its dependencies, so any edit to either layer would read as a new cycle.
   * The edge is the stored-form converter that {@code KumiteGame} names in its mapping, part of the
   * frozen persistence mapping below.
   */
  @Test
  void layers_areFreeOfCyclesBeyondTheStoredWinner() {
    slices()
        .assignedFrom(LAYERS)
        .should()
        .beFreeOfCycles()
        .ignoreDependency(equivalentTo(KumiteGame.class), equivalentTo(LegacyWinnerConverter.class))
        .check(CLASSES);
  }

  @Test
  void business_doesNotKnowTheApiShapes() {
    noClasses()
        .that()
        .resideInAnyPackage(BUSINESS)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(WEB)
        .check(CLASSES);
  }

  @Test
  void domainTypes_carryNoPersistenceMapping() {
    freeze(
            noClasses()
                .that()
                .resideInAPackage(DOMAIN)
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework.data.."))
        .check(CLASSES);
  }

  @Test
  void noUtilityPackage() {
    freeze(noClasses().should().resideInAPackage("..util..")).check(CLASSES);
  }

  private static ArchRule featureUsesNoOtherFeature(String feature, String... allowed) {
    String[] others =
        FEATURES.stream()
            .filter(other -> !other.equals(feature) && !List.of(allowed).contains(other))
            .map(other -> ROOT + "." + other + "..")
            .toArray(String[]::new);
    return noClasses()
        .that()
        .resideInAPackage(ROOT + "." + feature + "..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(others);
  }
}
