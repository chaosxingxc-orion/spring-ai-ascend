package ascend.springai.service.runtime.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces L1 module direction (ADR-0055): no production class under
 * {@code ascend.springai.service.runtime..} may import any class under
 * {@code ascend.springai.service.platform..}.
 *
 * <p>Strict generalisation of {@link TenantPropagationPurityTest} (which guards the
 * single {@code TenantContextHolder} class for Rule 21). At L1 we permit
 * {@code agent-platform -> agent-runtime} for the HTTP run handoff, but the reverse
 * direction stays forbidden at both pom and source levels.
 *
 * <p>Rationale: agent-platform owns request-scoped concerns (HTTP, JWT, ThreadLocal
 * tenant binding, idempotency entry behaviour). Runtime code runs in non-request
 * contexts (timer-driven resumes, async orchestration) where those concerns are
 * undefined; importing them would silently produce null-tenant / no-request bugs.
 *
 * <p>Test classes are intentionally excluded — the importer skips
 * {@code Tests.class} via {@link ImportOption.DoNotIncludeTests} so that
 * test-only utilities may legitimately reference platform internals.
 *
 * <p>Enforcer row: {@code docs/governance/enforcers.yaml#E2}.
 */
class RuntimeMustNotDependOnPlatformTest {

    private static final JavaClasses RUNTIME_MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("ascend.springai.service.runtime");

    @Test
    void runtime_main_sources_must_not_depend_on_any_platform_class() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("ascend.springai.service.runtime..")
                .should().dependOnClassesThat()
                .resideInAPackage("ascend.springai.service.platform..")
                .because("ADR-0055: runtime must not depend on platform; "
                        + "platform owns request-scoped concerns invalid in non-request contexts. "
                        + "Enforcer row E2 in docs/governance/enforcers.yaml.");
        rule.check(RUNTIME_MAIN_CLASSES);
    }
}
