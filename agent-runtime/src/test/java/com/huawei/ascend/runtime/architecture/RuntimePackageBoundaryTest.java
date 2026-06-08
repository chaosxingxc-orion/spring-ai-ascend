package com.huawei.ascend.runtime.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guards the five-business-layer flat structure: each layer keeps only its
 * allowed boundary sub-packages (no implementation sub-package creep), and the
 * cross-layer dependency rules hold. These rules are what keep the runtime at
 * "five flat modules" over time — a re-introduced {@code engine.event} /
 * {@code session.store} / {@code access.protocol} package fails the build.
 *
 * <p>Every structural rule uses {@code allowEmptyShould(false)} so it fails
 * rather than passing vacuously if a package rename ever makes its subject set
 * empty.
 */
class RuntimePackageBoundaryTest {

    private static final JavaClasses RUNTIME_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.huawei.ascend.runtime");

    @Test
    void engineHasOnlyApiSpiServiceOpenjiuwenSubpackages() {
        ArchRule rule = classes()
                .that().resideInAPackage("..runtime.engine..")
                .should().resideInAnyPackage(
                        "com.huawei.ascend.runtime.engine",
                        "com.huawei.ascend.runtime.engine.api..",
                        "com.huawei.ascend.runtime.engine.spi..",
                        "com.huawei.ascend.runtime.engine.service..",
                        "com.huawei.ascend.runtime.engine.openjiuwen..")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void accessHasOnlyA2aApiOutputSubpackages() {
        ArchRule rule = classes()
                .that().resideInAPackage("..runtime.access..")
                .should().resideInAnyPackage(
                        "com.huawei.ascend.runtime.access",
                        "com.huawei.ascend.runtime.access.a2a..",
                        "com.huawei.ascend.runtime.access.api..",
                        "com.huawei.ascend.runtime.access.output..")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void sessionHasOnlyApiSubpackage() {
        ArchRule rule = classes()
                .that().resideInAPackage("..runtime.session..")
                .should().resideInAnyPackage(
                        "com.huawei.ascend.runtime.session",
                        "com.huawei.ascend.runtime.session.api..")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void controlHasOnlyApiSubpackage() {
        ArchRule rule = classes()
                .that().resideInAPackage("..runtime.control..")
                .should().resideInAnyPackage(
                        "com.huawei.ascend.runtime.control",
                        "com.huawei.ascend.runtime.control.api..")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void queueIsFlat() {
        ArchRule rule = classes()
                .that().resideInAPackage("..runtime.queue..")
                .should().resideInAPackage("com.huawei.ascend.runtime.queue")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void openJiuwenAdapterLivesUnderEngineOpenjiuwen() {
        ArchRule rule = classes()
                .that().resideInAPackage("..openjiuwen..")
                .should().resideInAPackage("com.huawei.ascend.runtime.engine.openjiuwen..")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void accessDoesNotDependOnOpenJiuwenAdapter() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..runtime.access..")
                .should().dependOnClassesThat()
                .resideInAPackage("..runtime.engine.openjiuwen..");
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void controlDoesNotDependOnOpenJiuwenAdapter() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..runtime.control..")
                .should().dependOnClassesThat()
                .resideInAPackage("..runtime.engine.openjiuwen..");
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void commonDependsOnlyOnTheJdk() {
        // common is the neutral vocabulary: it must not reach into any business
        // layer or framework, so every other layer stays free to import it.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..runtime.common..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..runtime.access..",
                        "..runtime.session..",
                        "..runtime.queue..",
                        "..runtime.control..",
                        "..runtime.engine..",
                        "..runtime.app..",
                        "org.springframework..",
                        "org.a2aproject..",
                        "com.openjiuwen..");
        rule.check(RUNTIME_CLASSES);
    }
}
