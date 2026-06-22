package com.huawei.ascend.bus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPI-purity harness for the C3 forwarding substrate (Stage 7, slice 5).
 *
 * <p>Symmetric to {@link AgentBusSpiPurityTest} but scoped to
 * {@code com.huawei.ascend.bus.forwarding..} (the forwarding SPI + runtime
 * package). Stage 7-11 shipped the pure-Java domain model, ports, state machine,
 * worker and loop only — no Spring, no JDBC, no broker client. Stage 12 (decision
 * §4 permit) licenses Spring JDBC inside ONE subpackage —
 * {@code com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc} — so the
 * Spring / JDBC / javax.sql rules below exempt that adapter package. Stage 15
 * (decision §6.1 item 4 lifted to scaffold level) licenses the A2A SDK inside ONE
 * more subpackage — {@code com.huawei.ascend.bus.forwarding.runtime.transport.a2a}
 * — so the A2A rule below exempts that adapter package. Everything else (ports,
 * state machine, worker, loop) stays pure Java. §6.2 always-forbids concrete
 * broker / MQ, Task state, payload body everywhere (those rules are NOT exempted
 * — A2A is HTTP JSON-RPC, not Kafka / RabbitMQ / NATS).
 *
 * <p>One {@code @Test} per forbidden technology so a violation reports the exact
 * offending dependency. Test classes are excluded — the rule constrains the
 * shipped forwarding surface, not the in-memory test doubles.
 *
 * <p>Authority: Stage 7 plan §3 slice 4 boundary;
 * {@code architecture/L2-Low-Level-Design/agent-bus/forwarding-outbox-inbox.md §3}.
 */
class AgentBusForwardingSpiPurityTest {

    /**
     * Production forwarding classes only ({@code com.huawei.ascend.bus.forwarding}
     * and sub-packages). Test classes are excluded so the in-memory test doubles
     * (which may use the framework of the day) do not weaken the rule.
     */
    private static final JavaClasses FORWARDING = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.huawei.ascend.bus.forwarding");

    @Test
    void forwarding_core_does_not_import_spring_outside_jdbc_adapter() {
        noClasses().that().resideInAPackage("com.huawei.ascend.bus.forwarding..")
                .and().resideOutsideOfPackage("com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .because("Stage 12: Spring JDBC is licensed only inside the persistence.jdbc "
                       + "adapter subpackage; the forwarding ports / state machine / worker / loop "
                       + "stay pure Java (decision §4 Stage 12 permit, §6.2 unchanged).")
                .check(FORWARDING);
    }

    @Test
    void forwarding_core_does_not_import_jdbc_outside_jdbc_adapter() {
        noClasses().that().resideInAPackage("com.huawei.ascend.bus.forwarding..")
                .and().resideOutsideOfPackage("com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc..")
                .should().dependOnClassesThat().resideInAPackage("java.sql..")
                .because("Stage 12: java.sql is licensed only inside the persistence.jdbc adapter; "
                       + "the forwarding core (ports / state machine / worker / loop) must not "
                       + "depend on JDBC (decision §4 Stage 12 permit).")
                .check(FORWARDING);
    }

    @Test
    void forwarding_core_does_not_import_javax_sql_outside_jdbc_adapter() {
        noClasses().that().resideInAPackage("com.huawei.ascend.bus.forwarding..")
                .and().resideOutsideOfPackage("com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc..")
                .should().dependOnClassesThat().resideInAPackage("javax.sql..")
                .because("Stage 12: javax.sql (DataSource etc.) is licensed only inside the "
                       + "persistence.jdbc adapter; the forwarding core stays pure Java "
                       + "(decision §4 Stage 12 permit).")
                .check(FORWARDING);
    }

    @Test
    void forwarding_does_not_import_hikari() {
        noClasses().that().resideInAPackage("com.huawei.ascend.bus.forwarding..")
                .should().dependOnClassesThat().resideInAPackage("com.zaxxer.hikari..")
                .because("Stage 7 forwarding must not depend on a connection pool; real "
                       + "persistence is Stage 8.")
                .check(FORWARDING);
    }

    @Test
    void forwarding_does_not_import_jackson() {
        noClasses().that().resideInAPackage("com.huawei.ascend.bus.forwarding..")
                .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson..")
                .because("forwarding substrate is transport-agnostic; serialisation belongs in "
                       + "the wire binding layer, not the envelope / state-machine surface.")
                .check(FORWARDING);
    }

    @Test
    void forwarding_does_not_import_project_reactor() {
        noClasses().that().resideInAPackage("com.huawei.ascend.bus.forwarding..")
                .should().dependOnClassesThat().resideInAPackage("reactor..")
                .because("forwarding substrate is pure Java; java.util.concurrent.Flow is the "
                       + "allowed reactive-streams abstraction, not Project Reactor.")
                .check(FORWARDING);
    }

    @Test
    void forwarding_does_not_import_kafka() {
        noClasses().that().resideInAPackage("com.huawei.ascend.bus.forwarding..")
                .should().dependOnClassesThat().resideInAPackage("org.apache.kafka..")
                .because("forwarding substrate is broker-agnostic; Kafka is a Stage 8+ candidate "
                       + "binding, never a Stage 7 dependency (C3 is database outbox, not Kafka).")
                .check(FORWARDING);
    }

    @Test
    void forwarding_does_not_import_nats() {
        noClasses().that().resideInAPackage("com.huawei.ascend.bus.forwarding..")
                .should().dependOnClassesThat().resideInAPackage("io.nats..")
                .because("forwarding substrate is broker-agnostic; NATS is a Stage 8+ candidate, "
                       + "never a Stage 7 dependency.")
                .check(FORWARDING);
    }

    @Test
    void forwarding_does_not_import_jakarta_servlet() {
        noClasses().that().resideInAPackage("com.huawei.ascend.bus.forwarding..")
                .should().dependOnClassesThat().resideInAPackage("jakarta.servlet..")
                .because("forwarding substrate is transport-agnostic; the Servlet API belongs in "
                       + "an HTTP wire binding, never in the Stage 7 surface.")
                .check(FORWARDING);
    }

    @Test
    void forwarding_does_not_import_netty() {
        noClasses().that().resideInAPackage("com.huawei.ascend.bus.forwarding..")
                .should().dependOnClassesThat().resideInAPackage("io.netty..")
                .because("forwarding substrate is transport-agnostic; Netty is a network runtime, "
                       + "never a Stage 7 contract-surface dependency.")
                .check(FORWARDING);
    }

    @Test
    void forwarding_core_does_not_import_a2a_outside_transport_adapter() {
        noClasses().that().resideInAPackage("com.huawei.ascend.bus.forwarding..")
                .and().resideOutsideOfPackage("com.huawei.ascend.bus.forwarding.runtime.transport.a2a..")
                .should().dependOnClassesThat().resideInAPackage("org.a2aproject..")
                .because("Stage 15: the A2A SDK client is licensed only inside the transport.a2a "
                       + "adapter subpackage; the forwarding ports / state machine / worker / loop "
                       + "stay transport-agnostic (decision §6.1 item 4 lifted to scaffold level, "
                       + "§6.2 unchanged — A2A is HTTP JSON-RPC, not a concrete broker / MQ).")
                .check(FORWARDING);
    }

    // ---- import-liveness guard -------------------------------------------

    /**
     * Guards against an accidental empty import (e.g. a typo'd package path)
     * silently passing every {@code noClasses} rule above — an empty
     * {@link JavaClasses} set vacuously satisfies "no classes depend on X".
     */
    @Test
    void forwarding_import_is_non_empty() {
        assertThat(FORWARDING)
                .as("forwarding production class import must be non-empty (liveness guard)")
                .isNotEmpty();
    }
}
