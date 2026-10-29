package com.edutrack.desktop;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Guards the Clean Architecture rules that Maven modules alone cannot enforce
 * (the desktop module depends on infrastructure only to wire it at runtime).
 */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.edutrack");
    }

    @Test
    void userInterfaceTalksToApplicationServicesNotToInfrastructure() {
        noClasses().that().resideInAPackage("com.edutrack.desktop.ui..")
                .should().dependOnClassesThat().resideInAPackage("com.edutrack.infrastructure..")
                .check(classes);
    }

    @Test
    void domainDependsOnNothingButTheJdk() {
        noClasses().that().resideInAPackage("com.edutrack.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta..", "javafx..", "com.edutrack.application..",
                        "com.edutrack.infrastructure..", "com.edutrack.desktop..")
                .check(classes);
    }

    @Test
    void applicationLayerIsFrameworkFree() {
        noClasses().that().resideInAPackage("com.edutrack.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta..", "javafx..",
                        "com.edutrack.infrastructure..", "com.edutrack.desktop..")
                .check(classes);
    }

    @Test
    void domainPackagesHaveNoCycles() {
        slices().matching("com.edutrack.domain.(*)..").should().beFreeOfCycles().check(classes);
    }
}
