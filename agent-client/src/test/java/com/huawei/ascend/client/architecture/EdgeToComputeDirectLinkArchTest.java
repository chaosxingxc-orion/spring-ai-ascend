package com.huawei.ascend.client.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Edge↔Compute direct-link prohibition. Authority: CLAUDE.md Rule R-I
 * sub-clause .b (Edge↔Compute Ingress Routing); ADR-0089 (Edge-Plane
 * Ingress Gateway Mandate).
 *
 * <p>Modules whose {@code deployment_plane} is {@code edge} (today:
 * agent-client) MUST NOT import any production class under
 * {@code com.huawei.ascend.service..}, {@code com.huawei.ascend.engine..}, or
 * {@code com.huawei.ascend.middleware..}. Cross-plane traffic flows
 * exclusively through {@link com.huawei.ascend.bus.spi.ingress.IngressGateway}
 * whose wire schema is {@code docs/contracts/ingress-envelope.v1.yaml}.
 *
 * <p>Vacuous-but-armed today: agent-client is skeleton (no production
 * java code). When the W3+ SDK lands, this test starts gating PRs that
 * try to take shortcuts into compute_control plane internals.
 *
 * <p>Enforcer ID: E143.
 */
class EdgeToComputeDirectLinkArchTest {

    private static final JavaClasses CLIENT_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.huawei.ascend.client");

    @Test
    void edge_does_not_import_compute_control_service_module() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.client..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.huawei.ascend.service..")
                .because("Rule R-I sub-clause .b: agent-client MUST route through "
                       + "com.huawei.ascend.bus.spi.ingress.IngressGateway, not call agent-service directly");
        rule.check(CLIENT_CLASSES);
    }

    @Test
    void edge_does_not_import_compute_control_engine_module() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.client..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.huawei.ascend.engine..")
                .because("Rule R-I sub-clause .b: edge plane has no business reaching into engine internals");
        rule.check(CLIENT_CLASSES);
    }

    @Test
    void edge_does_not_import_compute_control_middleware_module() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.client..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.huawei.ascend.middleware..")
                .because("Rule R-I sub-clause .b: edge plane has no business reaching into middleware internals");
        rule.check(CLIENT_CLASSES);
    }
}
