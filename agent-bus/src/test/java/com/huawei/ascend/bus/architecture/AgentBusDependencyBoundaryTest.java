package com.huawei.ascend.bus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Module-boundary harness for {@code agent-bus} (Stage 1, Slice 1).
 *
 * <p>Asserts that {@code agent-bus} production code depends on NONE of its sibling
 * platform modules. {@code agent-bus} is the Bus &amp; State Hub plane (Rule R-I /
 * P-I) and sits upstream of the {@code compute_control} plane — it provides the
 * cross-plane SPI surface that others depend on, so it must never reach sideways
 * into a sibling. Authority: {@code agent-bus/module-metadata.yaml}
 * {@code forbidden_dependencies}; CLAUDE.md Rule R-C sub-clause .b (Independent
 * Module Evolution).
 *
 * <p>One {@code @Test} per forbidden sibling module so a violation reports the
 * exact offending dependency rather than a single aggregate failure. Mirrors the
 * style of {@code EdgeToComputeDirectLinkArchTest}. ArchUnit is a test-scope
 * dependency only (declared in {@code agent-bus/pom.xml}); it never enters the
 * production dependency graph.
 *
 * <p>Assertion ID: HA-001.
 */
class AgentBusDependencyBoundaryTest {

    /**
     * All production classes under {@code com.huawei.ascend.bus}. Test classes
     * ({@code target/test-classes}) are excluded so the rule only constrains the
     * shipped SPI surface.
     */
    private static final JavaClasses BUS_PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.huawei.ascend.bus");

    @Test
    void bus_does_not_depend_on_agent_service() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.bus..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.huawei.ascend.service..")
                .because("agent-bus is upstream of compute_control; it must not depend on "
                       + "agent-runtime (module-metadata.yaml forbidden_dependencies).");
        rule.check(BUS_PRODUCTION);
    }

    @Test
    void bus_does_not_depend_on_agent_execution_engine() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.bus..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.huawei.ascend.engine..")
                .because("agent-bus is upstream of compute_control; it must not depend on "
                       + "agent-core (module-metadata.yaml forbidden_dependencies). "
                       + "Note: com.huawei.ascend.bus.spi.engine is the bus's OWN engine SPI "
                       + "package and is not matched by this rule.");
        rule.check(BUS_PRODUCTION);
    }

    @Test
    void bus_does_not_depend_on_agent_client() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.bus..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.huawei.ascend.client..")
                .because("agent-bus is upstream of the edge plane; it must not depend on "
                       + "agent-client (module-metadata.yaml forbidden_dependencies).");
        rule.check(BUS_PRODUCTION);
    }

    @Test
    void bus_does_not_depend_on_agent_middleware() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.bus..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.huawei.ascend.middleware..")
                .because("agent-bus is upstream of compute_control; it must not depend on "
                       + "agent-middleware (module-metadata.yaml forbidden_dependencies).");
        rule.check(BUS_PRODUCTION);
    }

    @Test
    void bus_does_not_depend_on_agent_evolve() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.bus..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.huawei.ascend.evolve..")
                .because("agent-bus is upstream of agent-evolve; it must not depend on "
                       + "agent-evolve (module-metadata.yaml forbidden_dependencies).");
        rule.check(BUS_PRODUCTION);
    }

    // ---- import-liveness guard (MI-004 follow-up) -------------------------

    /**
     * Guards against an accidental empty import (e.g. a typo'd package path)
     * silently passing every {@code noClasses} rule above — an empty
     * {@link JavaClasses} set vacuously satisfies "no bus classes depend on X".
     * MI-004.
     */
    @Test
    void bus_production_import_is_non_empty() {
        assertThat(BUS_PRODUCTION)
                .as("bus production class import must be non-empty (MI-004 liveness guard)")
                .isNotEmpty();
    }
}
