package com.management.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * The layer boundaries: gateways, business and data stay apart.
 *
 * <p>Rules the code already passes are strict. Rules it does not yet pass are frozen: recorded
 * violations in {@code config/archunit/frozen} are tolerated, new ones fail the build, and fixed
 * ones leave the store. See {@code src/main/java/com/management/README.md}.
 */
class ArchitectureTest {

  private static final String[] GATEWAYS = {"..controllers..", "..dto..", "..mappers.."};
  private static final String[] BUSINESS = {"..services..", "..rules.."};
  private static final String[] DOMAIN_AND_BUSINESS = {
    "..services..", "..rules..", "..models..", "..enums.."
  };

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages("com.management");

  @Test
  void controllers_neverReachTheDataLayer() {
    noClasses()
        .that()
        .resideInAPackage("..controllers..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..repositories..")
        .check(CLASSES);
  }

  @Test
  void business_neverDependsOnControllersOrMappers() {
    noClasses()
        .that()
        .resideInAnyPackage(DOMAIN_AND_BUSINESS)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..controllers..", "..mappers..")
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
        .resideInAPackage("..repositories..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..services..", "..rules..", "..controllers..", "..dto..", "..mappers..")
        .check(CLASSES);
  }

  @Test
  void gateways_neverHoldBusinessRules() {
    noClasses()
        .that()
        .resideInAnyPackage(GATEWAYS)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..rules..", "..repositories..")
        .check(CLASSES);
  }

  @Test
  void business_doesNotKnowTheApiShapes() {
    freeze(
            noClasses()
                .that()
                .resideInAnyPackage(BUSINESS)
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..dto.."))
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
  void packages_areFreeOfCycles() {
    freeze(slices().matching("com.management.(*)..").should().beFreeOfCycles()).check(CLASSES);
  }

  @Test
  void noUtilityPackage() {
    freeze(noClasses().should().resideInAPackage("..util..")).check(CLASSES);
  }
}
