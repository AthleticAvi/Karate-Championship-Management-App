package com.management.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.AndFullName.Predicates.fullNameMatching;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

/**
 * The layer boundaries: gateways, business and data stay apart, inside every feature.
 *
 * <p>Every rule is strict. A rule the code cannot yet pass may be frozen, its known violations
 * recorded in {@code config/archunit/frozen}: none is today. See {@code
 * src/main/java/com/management/README.md}.
 */
class ArchitectureTest {

  private static final String ROOT = "com.management";
  private static final Set<String> LAYER_NAMES =
      Set.of("web", "service", "rules", "domain", "data");

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages(ROOT);

  /** A feature is a top-level package split into layers, so a new one is checked unasked. */
  private static final Set<String> FEATURES =
      CLASSES.stream()
          .map(ArchitectureTest::packagePath)
          .filter(path -> path.size() > 3 && LAYER_NAMES.contains(path.get(3)))
          .map(path -> path.get(2))
          .collect(Collectors.toSet());

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
   * One slice per layer of each feature, such as {@code kumitegame.domain}, and one per shared
   * package. A layer's sub-packages share its slice. Coarser than the old layer packages: a
   * feature's web holds what controllers, dto and mappers were, and its domain what models, enums
   * and the exception types were, so a cycle inside one of those is no longer seen.
   */
  private static final SliceAssignment LAYERS =
      new SliceAssignment() {
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
          List<String> path = packagePath(javaClass);
          if (path.size() < 3) {
            return SliceIdentifier.ignore();
          }
          return FEATURES.contains(path.get(2)) && path.size() > 3
              ? SliceIdentifier.of(path.get(2) + "." + path.get(3))
              : SliceIdentifier.of(path.get(2));
        }

        @Override
        public String getDescription() {
          return "layers";
        }
      };

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

  /**
   * {@code kumitegame} may use {@code athlete} and {@code referee}, the identities a game names. No
   * other feature uses another: a check that needs the games, such as refusing to delete an athlete
   * a game names, is an interface the athlete feature owns and the game feature implements.
   */
  @Test
  void features_dependOnlyKumiteGameOnAthleteAndReferee() {
    assertThat(FEATURES).contains("kumitegame", "athlete", "referee", "account");
    CompositeArchRule.of(
            FEATURES.stream()
                .map(feature ->
                    featureUsesNoOtherFeature(
                        feature,
                        feature.equals("kumitegame") ? Set.of("athlete", "referee") : Set.of()))
                .toList())
        .check(CLASSES);
  }

  /** The shared web package, the controllers, and the request and response records. */
  @Test
  void documentationTypes_stayInTheGateways() {
    noClasses()
        .that()
        .resideOutsideOfPackage(WEB)
        .or()
        .haveSimpleNameEndingWith("Mapper")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("io.swagger..", "org.springdoc..")
        .check(CLASSES);
  }

  /**
   * The security package, the controllers, and the shared web package, where the exception handler
   * turns a security failure into an answer. Not the request and response records or the mappers.
   */
  @Test
  void securityTypes_stayInTheGateways() {
    noClasses()
        .that()
        .resideOutsideOfPackages("..security..", ROOT + ".web")
        .and()
        .areNotAnnotatedWith(RestController.class)
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
        .because("one injected clock is what keeps game time the same on every instance")
        .check(CLASSES);
  }

  @Test
  void layers_areFreeOfCycles() {
    slices().assignedFrom(LAYERS).should().beFreeOfCycles().check(CLASSES);
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

  /** Every feature's domain is apart from its stored form: see {@code [pattern]} there. */
  @Test
  void domainTypes_carryNoPersistenceMapping() {
    noClasses()
        .that()
        .resideInAPackage(DOMAIN)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("org.springframework.data..")
        .check(CLASSES);
  }

  @Test
  void noUtilityPackage() {
    noClasses().should().resideInAPackage("..util..").check(CLASSES);
  }

  private static List<String> packagePath(JavaClass type) {
    return List.of(type.getPackageName().split("\\."));
  }

  private static ArchRule featureUsesNoOtherFeature(String feature, Set<String> allowed) {
    String[] others =
        FEATURES.stream()
            .filter(other -> !other.equals(feature) && !allowed.contains(other))
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
