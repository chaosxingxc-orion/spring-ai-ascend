package com.huawei.ascend.service.runtime.architecture;

import com.huawei.ascend.engine.orchestration.spi.RunMode;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Generalised SPI purity enforcer for CLAUDE.md Rule R-A (Business/Platform Decoupling)
 * and Rule R-D.a (SPI + DFX + TCK Co-Design). Authority: ADR-0064, ADR-0067.
 *
 * <p>Existing test MemorySpiArchTest covers runtime.memory.spi only and OrchestrationSpiArchTest
 * covers runtime.orchestration.spi only. This test generalises the contract: ANY package under
 * {@code com.huawei.ascend..spi..} (current or future) MUST remain free of Spring, platform,
 * inmemory-impl, and resilience-impl dependencies so SPI interfaces stay stable for downstream
 * developers extending the platform without source patches.
 *
 * <p>Vacuous-but-armed: when a new SPI package lands (e.g. {@code runtime.llm.spi}), this test
 * picks it up automatically — no test edits required.
 *
 * <p>Enforcer ID: E48.
 */
class SpiPurityGeneralizedArchTest {

    private static final JavaClasses ALL_RUNTIME_CLASSES = new ClassFileImporter()
            .importPackages("com.huawei.ascend");

    @Test
    void any_spi_package_does_not_depend_on_spring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend..spi..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework..");
        rule.check(ALL_RUNTIME_CLASSES);
    }

    @Test
    void any_spi_package_does_not_depend_on_platform() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend..spi..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.huawei.ascend.service.platform..");
        rule.check(ALL_RUNTIME_CLASSES);
    }

    @Test
    void any_spi_package_does_not_depend_on_inmemory_reference_impls() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend..spi..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.huawei.ascend.service.runtime.orchestration.inmemory..");
        rule.check(ALL_RUNTIME_CLASSES);
    }

    @Test
    void any_spi_package_does_not_depend_on_micrometer_or_otel() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend..spi..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("io.micrometer..", "io.opentelemetry..");
        rule.check(ALL_RUNTIME_CLASSES);
    }

    /**
     * v2.0.0-rc3 strengthening (cross-constraint audit α-4 / β-2): the s2c.spi
     * package was moved here from runtime.s2c so it now literally imports
     * only java.* and same-spi-package siblings. Assert that strictly.
     *
     * <p>orchestration.spi is excluded because it depends on the kernel runs.*
     * domain types (Run, RunMode, RunRepository) which are intrinsic to the
     * orchestrator SPI surface — that exception predates rc3 and is intentional.
     */
    @Test
    void s2c_spi_imports_only_java_and_same_package_siblings() {
        ArchRule rule = classes()
                .that().resideInAPackage("com.huawei.ascend.bus.spi.s2c..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "com.huawei.ascend.bus.spi.s2c..");
        rule.check(ALL_RUNTIME_CLASSES);
    }

    @Test
    void agent_middleware_spi_imports_only_java_and_same_package_siblings() {
        ArchRule rule = classes()
                .that().resideInAPackage("com.huawei.ascend.middleware..spi..")
                .should(importOnlyJavaOrSamePackageSiblings());
        rule.check(ALL_RUNTIME_CLASSES);
    }

    private static ArchCondition<JavaClass> importOnlyJavaOrSamePackageSiblings() {
        return new ArchCondition<>("import only java.* or same-package SPI siblings") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetPackage = target.getPackageName();
                    if (targetPackage.startsWith("com.huawei.ascend")
                            && !targetPackage.equals(item.getPackageName())) {
                        String message = item.getName() + " depends on " + target.getName()
                                + " via " + dependency.getDescription();
                        events.add(SimpleConditionEvent.violated(item, message));
                    }
                }
            }
        };
    }
}
