package ascend.springai.client.architecture;

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
 * {@code ascend.springai.service..}, {@code ascend.springai.engine..}, or
 * {@code ascend.springai.middleware..}. Cross-plane traffic flows
 * exclusively through {@link ascend.springai.bus.spi.ingress.IngressGateway}
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
            .importPackages("ascend.springai.client");

    @Test
    void edge_does_not_import_compute_control_service_module() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("ascend.springai.client..")
                .should().dependOnClassesThat()
                .resideInAPackage("ascend.springai.service..")
                .because("Rule R-I sub-clause .b: agent-client MUST route through "
                       + "ascend.springai.bus.spi.ingress.IngressGateway, not call agent-service directly");
        rule.check(CLIENT_CLASSES);
    }

    @Test
    void edge_does_not_import_compute_control_engine_module() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("ascend.springai.client..")
                .should().dependOnClassesThat()
                .resideInAPackage("ascend.springai.engine..")
                .because("Rule R-I sub-clause .b: edge plane has no business reaching into engine internals");
        rule.check(CLIENT_CLASSES);
    }

    @Test
    void edge_does_not_import_compute_control_middleware_module() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("ascend.springai.client..")
                .should().dependOnClassesThat()
                .resideInAPackage("ascend.springai.middleware..")
                .because("Rule R-I sub-clause .b: edge plane has no business reaching into middleware internals");
        rule.check(CLIENT_CLASSES);
    }
}
