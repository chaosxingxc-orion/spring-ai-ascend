package ascend.springai.service.runtime.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces ARCHITECTURE.md §4 #57 (Tenant attribute on every span).
 *
 * <p>Every Span emitted by the platform MUST carry {@code tenant.id} matching
 * {@code RunContext.tenantId()}. Direct emission paths that construct a span
 * without a tenant binding are forbidden.
 *
 * <p>At L1.x there is no concrete span emitter class (W2 trigger), so the rule is
 * structurally vacuous via {@code allowEmptyShould(true)}. It arms the moment a
 * class lands under {@code agent-service/src/main/.../runtime/observability/emit/} (post-Phase-C; pre-Phase-C: {@code agent-runtime/observability/emit/} per ADR-0078) or names containing
 * {@code SpanEmitter}/{@code SpanBuilder}. The negative invariant: no such class
 * may exist without depending on {@code RunContext} (the canonical tenant carrier
 * per Rule R-C.e + ADR-0023).
 *
 * <p>Enforcer E44. ADR-0061 §5.
 */
class SpanTenantAttributeRequiredTest {

    private static final JavaClasses RUNTIME_MAIN_CLASSES = new ClassFileImporter()
            .importPackages("ascend.springai.service.runtime");

    @Test
    void no_span_emitter_exists_without_run_context_dependency() {
        ArchRule rule = noClasses()
                .that().haveSimpleNameEndingWith("SpanEmitter")
                .or().haveSimpleNameEndingWith("SpanBuilder")
                .should().dependOnClassesThat()
                .haveSimpleName("DoesNotExist") // placeholder — the real check is the inverse below
                .allowEmptyShould(true);
        rule.check(RUNTIME_MAIN_CLASSES);
    }

    @Test
    void any_future_span_emitter_must_depend_on_RunContext() {
        // Positive form: if a class named *SpanEmitter or *SpanBuilder exists,
        // it MUST depend on RunContext (the tenant carrier per §4 #22 / Rule R-C.e).
        // Vacuous at L1.x (no such class).
        ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                .that().haveSimpleNameEndingWith("SpanEmitter")
                .or().haveSimpleNameEndingWith("SpanBuilder")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("ascend.springai.engine.orchestration.spi.RunContext")
                .allowEmptyShould(true);
        rule.check(RUNTIME_MAIN_CLASSES);
    }
}
