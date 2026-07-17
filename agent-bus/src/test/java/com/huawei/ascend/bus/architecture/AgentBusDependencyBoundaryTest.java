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

    @Test
    void bus_does_not_depend_on_agent_runtime() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.bus..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.huawei.ascend.runtime..")
                .because("agent-bus production must stay free of agent-runtime; Stage 17 "
                       + "introduces a test-only dependency (the C3 end-to-end IT boots a "
                       + "real LocalA2aRuntimeHost), but the shipped SPI surface must not "
                       + "reach into the compute_control runtime. Supersedes the stale "
                       + "'service..' guard left by the agent-service → agent-runtime "
                       + "rename in 034da8f7.");
        rule.check(BUS_PRODUCTION);
    }

    // ---- gateway / forwarding-runtime plane boundary (agent-bus layering, ADR-0163 + gateway-assembly-purify) ----

    /**
     * The gateway plane must not depend on ANY {@code forwarding.runtime} type
     * (the literal-full plane boundary — arch-driven gateway-assembly-purify, the
     * flagged ADR-0163 follow-on; G4 sign-off 2026-07-16, Q5a=(b)). Gateway wiring
     * ({@code gateway.runtime}) depends only on the forwarding SPI
     * ({@code forwarding.spi} incl. {@code forwarding.spi.broker}, where the shared
     * {@code BrokerControlDescriptor} codec now lives) + the shared
     * {@code forwarding.common} config + {@code bus.spi.ingress} — it injects the
     * broker / JDBC adapter beans (provided by
     * {@code RocketMqBrokerClientConfiguration} in {@code transport.broker.rocketmq}
     * + {@code AgentBusInfrastructureConfiguration} in {@code forwarding.common},
     * which owns the shared outbox / inbox / broker-client-properties) via
     * their SPI ports rather than constructing them.
     *
     * <p><b>Why the literal full (not the ADR-0163 accepted-drift scope
     * {@code forwarding.runtime.relay..}).</b> ADR-0163 scoped this rule to
     * {@code gateway↛forwarding.runtime.relay..} as an accepted drift: the to-be's
     * literal {@code gateway.runtime.. ↛ forwarding.runtime..} was then infeasible
     * because the gateway wired concrete {@code forwarding.runtime.*} adapters
     * (broker-common, rocketmq, JDBC outbox, transport resolver) +
     * {@code BrokerControlDescriptor}. gateway-assembly-purify removes EVERY one of
     * those: the 5 concrete-adapter {@code @Bean} move into forwarding adapter
     * {@code @Configuration}s, and {@code BrokerControlDescriptor} moves to
     * {@code forwarding.spi.broker} — so the gateway's {@code forwarding.runtime}
     * imports are now ZERO + the literal full rule holds (green, non-vacuous). This
     * supersedes the ADR-0163 deviation-b drift note that flagged this as a future
     * stricter rule.
     */
    @Test
    void gateway_runtime_does_not_depend_on_forwarding_runtime() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.bus.gateway.runtime..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.huawei.ascend.bus.forwarding.runtime..")
                .because("agent-bus layering (ADR-0163 + gateway-assembly-purify follow-on): "
                       + "the gateway plane wires ONLY the forwarding SPI + common config + the "
                       + "ingress SPI; the concrete broker / JDBC adapters + their wiring live in "
                       + "forwarding.runtime.* (transport.broker.rocketmq / persistence.jdbc / "
                       + "runtime.relay) and are injected as SPI ports, never constructed or "
                       + "imported by the gateway. The literal gateway.runtime.. ↛ forwarding.runtime.. "
                       + "holds because BrokerControlDescriptor (the prior sole residual) moved to "
                       + "forwarding.spi.broker.");
        rule.check(BUS_PRODUCTION);
    }

    /**
     * Liveness guard for the gateway↛forwarding.runtime rule above: if either
     * plane's package were empty (a typo'd path, or all wiring moved back out), the
     * rule would pass vacuously — an empty {@link JavaClasses} set satisfies "no
     * classes depend on X". Confirms both planes still ship wiring post-purify
     * (gateway.runtime: the gateway @Configuration + service + controller;
     * forwarding.runtime: the relay mechanism + the broker/jdbc adapter @Configurations).
     */
    @Test
    void gateway_and_forwarding_runtime_planes_are_non_empty() {
        JavaClasses gateway = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.huawei.ascend.bus.gateway.runtime");
        JavaClasses forwardingRuntime = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.huawei.ascend.bus.forwarding.runtime");
        assertThat(gateway)
                .as("gateway.runtime plane must ship wiring (liveness guard for gateway↛forwarding.runtime rule)")
                .isNotEmpty();
        assertThat(forwardingRuntime)
                .as("forwarding.runtime plane must ship wiring (liveness guard for gateway↛forwarding.runtime rule)")
                .isNotEmpty();
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
