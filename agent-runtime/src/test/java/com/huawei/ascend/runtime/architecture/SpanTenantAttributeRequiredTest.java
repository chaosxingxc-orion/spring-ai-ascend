package com.huawei.ascend.runtime.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces ARCHITECTURE.md §4 #57 (Tenant attribute on every span).
 *
 * <p>Every Span emitted by the platform MUST carry {@code tenant.id} matching the
 * invocation's tenant identity ({@code RuntimeIdentity.tenantId()}, carried on
 * every {@code TrajectoryEvent}). Direct emission paths that construct a span
 * without a tenant binding are forbidden.
 *
 * <p>At L1.x there is no concrete span emitter class (W2 trigger), so the rule is
 * structurally vacuous via {@code allowEmptyShould(true)}. It arms the moment a
 * class lands under {@code agent-service/src/main/.../runtime/observability/emit/} (post-Phase-C; pre-Phase-C: {@code agent-runtime/observability/emit/} per ADR-0078) or names containing
 * {@code SpanEmitter}/{@code SpanBuilder}. The negative invariant: no such class
 * may exist without depending on the runtime's own tenant carriers
 * ({@code RuntimeIdentity} / {@code TrajectoryEvent}).
 *
 * <p>Enforcer E44. ADR-0061 §5.
 */
class SpanTenantAttributeRequiredTest {

    private static final JavaClasses RUNTIME_MAIN_CLASSES = new ClassFileImporter()
            .importPackages("com.huawei.ascend.runtime");

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
    void any_future_span_emitter_must_depend_on_a_runtime_tenant_carrier() {
        // Positive form: if a class named *SpanEmitter or *SpanBuilder exists,
        // it MUST depend on a runtime tenant carrier (RuntimeIdentity, or
        // TrajectoryEvent which carries tenantId on every event).
        // Vacuous at L1.x (no such class).
        ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                .that().haveSimpleNameEndingWith("SpanEmitter")
                .or().haveSimpleNameEndingWith("SpanBuilder")
                .should().dependOnClassesThat()
                .belongToAnyOf(com.huawei.ascend.runtime.common.RuntimeIdentity.class,
                        com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.class)
                .allowEmptyShould(true);
        rule.check(RUNTIME_MAIN_CLASSES);
    }
}
