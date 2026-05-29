package com.huawei.ascend.bus.spi.engine;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces that orchestration.spi.* is pure-Java with no Spring or platform deps.
 * Per architecture §4.7: SPI packages import only java.*.
 * An executor implementation may depend on Spring; the SPI contract must not.
 */
class OrchestrationSpiArchTest {

    private static final JavaClasses RUNTIME_CLASSES = new ClassFileImporter()
            .importPackages("com.huawei.ascend.bus.spi.engine");

    @Test
    void orchestration_spi_has_no_spring_dependency() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.bus.spi.engine..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework..");
        rule.check(RUNTIME_CLASSES);
    }

    @Test
    void orchestration_spi_has_no_platform_dependency() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.bus.spi.engine..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.huawei.ascend.service.platform..");
        rule.check(RUNTIME_CLASSES);
    }
}
