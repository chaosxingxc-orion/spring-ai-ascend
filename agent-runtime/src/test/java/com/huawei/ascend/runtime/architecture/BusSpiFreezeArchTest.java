package com.huawei.ascend.runtime.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the agent-bus SPI design freeze: the {@code com.huawei.ascend.bus.spi.*}
 * surface is a design artefact with no validated implementation, and production
 * runtime code must not start consuming it before a re-opening ADR lifts the
 * freeze. The boundary of record for engine integration is
 * {@code com.huawei.ascend.runtime.engine.spi}; decision record at
 * {@code docs/logs/plans/2026-06-11-agent-bus-spi-decision.md}.
 */
class BusSpiFreezeArchTest {

    private static final JavaClasses RUNTIME_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.huawei.ascend.runtime");

    @Test
    void productionRuntimeDoesNotDependOnFrozenBusSpi() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.runtime..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.huawei.ascend.bus.spi..");
        rule.check(RUNTIME_CLASSES);
    }
}
