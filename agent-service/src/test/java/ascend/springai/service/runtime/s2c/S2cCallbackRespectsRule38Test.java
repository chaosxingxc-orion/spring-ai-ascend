package ascend.springai.service.runtime.s2c;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces Rule 38 (No Thread.sleep in Business Code) for the s2c package
 * specifically -- the S2C call site is the most natural location to slip in
 * a busy-wait/sleep while waiting for the client response. The orchestrator
 * MUST instead route through SuspendSignal/checkpoint per Chronos Hydration
 * (P-H).
 *
 * <p>Authority: ADR-0074; CLAUDE.md Rule 38 (No Thread.sleep) + Rule 46
 * (S2C Callback Envelope + Lifecycle Bound).
 * Enforcer row: docs/governance/enforcers.yaml#E83 (post-review fix plan F /
 * P1-2: prior Javadoc cited #E84 which is the engine-envelope boot-validation
 * IT in agent-platform, not this no-Thread.sleep ArchUnit).
 */
class S2cCallbackRespectsRule38Test {

    private static final JavaClasses S2C_MAIN = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("ascend.springai.service.runtime.s2c");

    @Test
    void s2c_package_must_not_call_thread_sleep() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("ascend.springai.service.runtime.s2c..")
                .should().callMethod(Thread.class, "sleep", long.class)
                .orShould().callMethod(Thread.class, "sleep", long.class, int.class)
                .because("ADR-0074 + Rule 38: S2C wait must route through SuspendSignal "
                        + "(Chronos Hydration) -- no thread blocking, no busy-wait.");
        rule.check(S2C_MAIN);
    }
}
