package com.huawei.ascend.runtime.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guards the simplified runtime package structure after A2A SDK consolidation.
 * Engine sub-packages are restricted; framework adapters live under their
 * own sub-packages; common must stay framework-free.
 */
class RuntimePackageBoundaryTest {

    private static final JavaClasses RUNTIME_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.huawei.ascend.runtime");

    @Test
    void engineHasAllowedSubpackagesOnly() {
        ArchRule rule = classes()
                .that().resideInAPackage("..runtime.engine..")
                .should().resideInAnyPackage(
                        "com.huawei.ascend.runtime.engine",
                        "com.huawei.ascend.runtime.engine.a2a..",
                        "com.huawei.ascend.runtime.engine.agentscope..",
                        "com.huawei.ascend.runtime.engine.langgraph..",
                        "com.huawei.ascend.runtime.engine.openjiuwen..",
                        "com.huawei.ascend.runtime.engine.service..",
                        "com.huawei.ascend.runtime.engine.spi..")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void bootIsFlat() {
        ArchRule rule = classes()
                .that().resideInAPackage("..runtime.boot..")
                .should().resideInAPackage("com.huawei.ascend.runtime.boot")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void appHasNoSubpackages() {
        ArchRule rule = classes()
                .that().resideInAPackage("..runtime.app..")
                .should().resideInAPackage("com.huawei.ascend.runtime.app")
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
    void agentScopeAdapterLivesUnderEngineAgentscope() {
        ArchRule rule = classes()
                .that().resideInAPackage("..agentscope..")
                .should().resideInAPackage("com.huawei.ascend.runtime.engine.agentscope..")
                .allowEmptyShould(false);
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void neutralEngineSpiStaysFrameworkAgnostic() {
        // The northbound trajectory abstraction lives in engine.spi and must stay framework-neutral:
        // native framework events are consumed only in the per-framework adapter (engine.openjiuwen),
        // never leaked into the neutral SPI. Guards the owner-mandated abstraction boundary.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..runtime.engine.spi..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.openjiuwen..");
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void commonDependsOnlyOnTheJdk() {
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
                        "..runtime.boot..",
                        "org.springframework..",
                        "org.a2aproject..",
                        "com.openjiuwen..");
        rule.check(RUNTIME_CLASSES);
    }
}
