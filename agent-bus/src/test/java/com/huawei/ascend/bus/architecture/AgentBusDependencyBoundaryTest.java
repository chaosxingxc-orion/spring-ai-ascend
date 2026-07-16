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

    // ---- gateway / forwarding-runtime plane boundary (agent-bus layering, ADR-0163) ----

    /**
     * The gateway plane must not depend on the forwarding relay-runtime package
     * (the event-bus wiring's post-reorg home). Gateway wiring
     * ({@code gateway.runtime}) may depend on the forwarding SPI
     * ({@code forwarding.spi} incl. {@code forwarding.spi.broker}) + the shared
     * {@code forwarding.common} config + the broker-common types
     * ({@code forwarding.runtime.transport.broker}) + the broker adapters
     * ({@code forwarding.runtime.transport.broker.rocketmq}) + the JDBC outbox
     * adapter ({@code forwarding.runtime.persistence.jdbc}) + the transport resolver
     * ({@code forwarding.runtime.transport}) — but never on
     * {@code forwarding.runtime.relay..}, which is where the event-bus two-hop relay
     * mechanism + its wiring ({@code EventBusRelayWorker} / {@code RelayTick} /
     * {@code RelayDispatchLoop} / {@code EventBusRelayConfiguration} /
     * {@code EventBusRelaySchedulingConfig} / {@code RelayScheduler}) now live
     * (arch-driven forwarding-reorg, ADR-0163 supersedes ADR-0162's
     * {@code gateway↛eventbus.runtime} rule — the event-bus wiring moved from the
     * eliminated {@code eventbus.runtime} plane into {@code forwarding.runtime.relay}).
     *
     * <p><b>Why scoped to {@code forwarding.runtime.relay..} and not the broader
     * {@code forwarding.runtime..} the decision-tree Q5 prose names.</b> The to-be
     * module-delta lists the gateway as depending on {@code forwarding.runtime.transport.broker}
     * + {@code forwarding.runtime.persistence.jdbc} (broker-common config, the rocketmq
     * adapters, the JDBC outbox, the transport resolver) — i.e. the gateway WIRES
     * several {@code forwarding.runtime..} subpackages. A literal
     * {@code gateway.runtime.. ↛ forwarding.runtime..} rule would fail on those
     * legitimate wiring dependencies; the strictest GREEN rule that still generalises
     * ADR-0162's intent ("the gateway never reaches into the event-bus wiring") is
     * to name the relay package by its post-reorg location. See the
     * {@code forwarding-reorg/deviations.md} entry on this scope choice.
     */
    @Test
    void gateway_runtime_does_not_depend_on_forwarding_relay_runtime() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.bus.gateway.runtime..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.huawei.ascend.bus.forwarding.runtime.relay..")
                .because("agent-bus layering (ADR-0163, supersedes ADR-0162's "
                       + "gateway↛eventbus.runtime): the gateway plane wires the forwarding "
                       + "SPI + broker-common + broker adapters + the JDBC outbox, but never "
                       + "the event-bus two-hop relay mechanism / wiring, which the reorg "
                       + "folded from the eliminated eventbus.runtime plane into "
                       + "forwarding.runtime.relay. Naming the relay package by its post-reorg "
                       + "location is the strictest green generalisation of the prior "
                       + "gateway↛eventbus rule.");
        rule.check(BUS_PRODUCTION);
    }

    /**
     * Liveness guard for the gateway↛forwarding.runtime.relay rule above: if either
     * plane's package were empty (a typo'd path, or all wiring moved back out), the
     * rule would pass vacuously — an empty {@link JavaClasses} set satisfies "no
     * classes depend on X". Confirms both planes still ship wiring post-reorg
     * (gateway.runtime: the gateway @Configuration + service; forwarding.runtime.relay:
     * the relay mechanism + the event-bus wiring the rule guards).
     */
    @Test
    void gateway_and_forwarding_relay_runtime_planes_are_non_empty() {
        JavaClasses gateway = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.huawei.ascend.bus.gateway.runtime");
        JavaClasses relayRuntime = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.huawei.ascend.bus.forwarding.runtime.relay");
        assertThat(gateway)
                .as("gateway.runtime plane must ship wiring (liveness guard for gateway↛forwarding.runtime.relay rule)")
                .isNotEmpty();
        assertThat(relayRuntime)
                .as("forwarding.runtime.relay plane must ship wiring (liveness guard for gateway↛forwarding.runtime.relay rule)")
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
