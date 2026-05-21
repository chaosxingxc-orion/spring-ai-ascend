package com.huawei.ascend.service.platform.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces CLAUDE.md Rule G-1.a (Layered 4+1 Discipline) at the source-code layer.
 *
 * <p>Rule G-1.a mandates that every architecture artefact (docs/adr/*.yaml,
 * docs/L2/**, ARCHITECTURE.md, agent-*&#47;ARCHITECTURE.md, docs/logs/reviews/*.md)
 * declares a (level, view) front-matter pair. The doc-side enforcement lives
 * in Gate Rule R-G (architecture_artefact_front_matter).
 *
 * <p>This ArchUnit test is the source-code complement: when W2 introduces
 * typed Java references to a Layered-4+1 artefact (for example a future
 * {@code @LayeredFor(level = L0, view = SCENARIOS)} annotation, or a generated
 * Java enum mirroring the graph nodes), this test will assert that the
 * (level, view) pair on the importing class agrees with the pair declared by
 * the imported doc node.
 *
 * <p>Vacuous at L1.x — no Java class today references a Layered-4+1 doc node
 * through a typed handle, so the rule body is intentionally empty and uses
 * {@code allowEmptyShould(true)} per the existing pattern in
 * {@link McpReplaySurfaceArchTest}.
 *
 * <p>Enforcer E59. ADR-0068 (Layered 4+1 + Architecture Graph as Twin Sources
 * of Truth). Activates automatically when a typed Layered-4+1 handle is
 * introduced in W2 — the test will then constrain its usage without needing
 * to be rewritten.
 */
class ArchitectureLayeringTest {

    private static final JavaClasses PLATFORM_MAIN_CLASSES = new ClassFileImporter()
            .importPackages("com.huawei.ascend.service.platform");

    @Test
    void no_class_violates_the_layered_4plus1_frontier_yet_vacuous_at_L1_x() {
        // Placeholder body: when a typed @LayeredFor / Level enum / View enum
        // arrives in W2, replace the no-op rule below with the real frontier
        // constraint. The test name + Javadoc above is the contract the future
        // body must satisfy.
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.huawei.ascend.service.platform..")
                .should().accessClassesThat().resideInAnyPackage("__doc__.layered_4_plus_1.violation..")
                .allowEmptyShould(true);
        rule.check(PLATFORM_MAIN_CLASSES);
    }
}
