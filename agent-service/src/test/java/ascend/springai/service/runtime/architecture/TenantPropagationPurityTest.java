package ascend.springai.service.runtime.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces Rule 21 (Tenant Propagation Purity): production code in the runtime sub-tree
 * (post-Phase-C: agent-service.runtime.*; pre-Phase-C: agent-runtime per ADR-0078) MUST NOT
 * import TenantContextHolder (a request-scoped HTTP-edge ThreadLocal in the platform sub-tree;
 * post-Phase-C: agent-service.platform.*; pre-Phase-C: agent-platform per ADR-0078).
 *
 * <p>Rationale (§4 #22, ADR-0023): RunContext.tenantId() is the canonical tenant carrier
 * inside the runtime sub-tree (pre-Phase-C: agent-runtime). TenantContextHolder is valid only for the duration of an HTTP
 * request and is unavailable during timer-driven resumes or async orchestration.
 * Runtime code that reads the ThreadLocal would silently receive null in those contexts.
 *
 * <p>Test classes are intentionally excluded — TenantContextFilterTest legitimately reads
 * the holder to verify filter behaviour.
 */
class TenantPropagationPurityTest {

    private static final JavaClasses RUNTIME_MAIN_CLASSES = new ClassFileImporter()
            .importPackages("ascend.springai.service.runtime");

    @Test
    void runtime_production_code_must_not_read_tenant_context_holder() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("ascend.springai.service.runtime..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName(
                        "ascend.springai.service.platform.tenant.TenantContextHolder");
        rule.check(RUNTIME_MAIN_CLASSES);
    }
}
