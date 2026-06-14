package com.huawei.ascend.bus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * SPI-purity harness for {@code agent-bus} (Stage 1, Slice 1).
 *
 * <p>Asserts that the {@code com.huawei.ascend.bus.spi..} packages stay pure Java:
 * no Spring, no Reactor, no Jackson, no observability SDK, no broker runtime.
 * The SPI is the transport-agnostic contract surface that every plane binds to,
 * so dragging in a framework or broker runtime here forces every consumer onto
 * that same technology. Authority: L1 development view SPI-purity rule;
 * CLAUDE.md Rule R-I sub-clause .b; {@code IngressGateway} Javadoc
 * ("Pure Java — no Spring, no Reactor, no Jackson imports").
 *
 * <p>One {@code @Test} per forbidden technology so a violation reports the exact
 * offending import. Test classes are excluded so the rule only constrains the
 * shipped SPI surface.
 *
 * <p>Note: {@code java.util.concurrent.Flow} (used by {@code EnginePort}) is JDK
 * standard and is NOT {@code reactor..}; ArchUnit matches the package prefix, so
 * the JDK reactive-streams bridges is correctly allowed while Project Reactor is
 * correctly forbidden.
 *
 * <p>Assertion ID: HA-001.
 */
class AgentBusSpiPurityTest {

    /**
     * Production SPI classes only ({@code com.huawei.ascend.bus.spi} and
     * sub-packages). Test classes are excluded — the rule constrains the shipped
     * contract surface, not test scaffolding.
     */
    private static final JavaClasses SPI_PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.huawei.ascend.bus.spi");

    @Test
    void spi_does_not_import_spring() {
        noClasses()
                .that().resideInAPackage("com.huawei.ascend.bus.spi..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .because("agent-bus SPI must stay pure Java; Spring belongs in runtime bindings, "
                       + "never in the transport-agnostic contract surface.")
                .check(SPI_PRODUCTION);
    }

    @Test
    void spi_does_not_import_project_reactor() {
        noClasses()
                .that().resideInAPackage("com.huawei.ascend.bus.spi..")
                .should().dependOnClassesThat().resideInAPackage("reactor..")
                .because("agent-bus SPI must stay pure Java; java.util.concurrent.Flow is the "
                       + "allowed reactive-streams abstraction, not Project Reactor.")
                .check(SPI_PRODUCTION);
    }

    @Test
    void spi_does_not_import_jackson() {
        noClasses()
                .that().resideInAPackage("com.huawei.ascend.bus.spi..")
                .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson..")
                .because("agent-bus SPI must stay transport-agnostic; serialisation belongs in "
                       + "the wire binding layer, not the envelope contract.")
                .check(SPI_PRODUCTION);
    }

    @Test
    void spi_does_not_import_micrometer() {
        noClasses()
                .that().resideInAPackage("com.huawei.ascend.bus.spi..")
                .should().dependOnClassesThat().resideInAPackage("io.micrometer..")
                .because("agent-bus SPI must stay pure Java; metrics instrumentation belongs in "
                       + "runtime, not in the contract surface.")
                .check(SPI_PRODUCTION);
    }

    @Test
    void spi_does_not_import_opentelemetry() {
        noClasses()
                .that().resideInAPackage("com.huawei.ascend.bus.spi..")
                .should().dependOnClassesThat().resideInAPackage("io.opentelemetry..")
                .because("agent-bus SPI must stay pure Java; tracing SDK belongs in runtime, "
                       + "not in the contract surface.")
                .check(SPI_PRODUCTION);
    }

    @Test
    void spi_does_not_import_kafka() {
        noClasses()
                .that().resideInAPackage("com.huawei.ascend.bus.spi..")
                .should().dependOnClassesThat().resideInAPackage("org.apache.kafka..")
                .because("agent-bus SPI must stay broker-agnostic; Kafka is a candidate runtime "
                       + "binding, never an SPI dependency (per Stage 1 forbidden scope).")
                .check(SPI_PRODUCTION);
    }

    @Test
    void spi_does_not_import_nats() {
        noClasses()
                .that().resideInAPackage("com.huawei.ascend.bus.spi..")
                .should().dependOnClassesThat().resideInAPackage("io.nats..")
                .because("agent-bus SPI must stay broker-agnostic; NATS is a candidate runtime "
                       + "binding, never an SPI dependency (per Stage 1 forbidden scope).")
                .check(SPI_PRODUCTION);
    }
}
