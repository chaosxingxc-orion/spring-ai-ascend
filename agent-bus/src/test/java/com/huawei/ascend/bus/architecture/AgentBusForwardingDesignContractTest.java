package com.huawei.ascend.bus.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Design-level contract harness for {@code ICD-Agent-Bus-Forwarding}
 * (Stage 4, slices 1-3).
 *
 * <p>The MQ-like forwarding surface is contract-only in Stage 4 — there is no
 * broker runtime, no queue/mailbox/DLQ storage, no Task-state ownership change,
 * and no binding to a concrete broker product. This harness pins the design
 * invariants the ICD encodes, so a future edit that silently weakens them
 * (dropping the tenantId requirement, admitting a payload body on the envelope,
 * binding a concrete broker, leaking Task state into the forwarding channel,
 * detaching forwarding from the Stage 3 route handle) fails the build.
 *
 * <p>Authority: {@code docs/architecture/l0/05-contracts/human-readable/
 * ICD-agent-bus-forwarding.md} (HD4); Stage 4 boundary.
 *
 * <p>The document assertions read the ICD and the L1 README as plain text and
 * anchor on stable phrases. The Stage 4 boundary assertion uses ArchUnit to
 * prove no production package under {@code com.huawei.ascend.bus.spi} is named
 * {@code broker}/{@code queue}/{@code mailbox}/{@code dlq}/{@code replay} — the
 * trip-wire that forces an explicit decision when Stage 5 admits a broker
 * runtime.
 *
 * <p>The seven {@code @Test} method names are mirrored verbatim in the ICD's
 * {@code Contract Tests (design-level, 切片 3)} row, so a renamed assertion
 * surfaces as ICD/harness drift.
 */
class AgentBusForwardingDesignContractTest {

    private static final Path ICD = Path.of(
            "../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md");
    private static final Path L1_README = Path.of("../architecture/docs/L1/agent-bus/README.md");

    private static String icdText;
    private static String readmeText;

    @BeforeAll
    static void readDesignSources() throws Exception {
        assertThat(ICD)
                .as("ICD-Agent-Bus-Forwarding must be reachable from the surefire working "
                  + "directory (agent-bus module basedir)")
                .exists();
        icdText = Files.readString(ICD);

        assertThat(L1_README)
                .as("agent-bus L1 README must be reachable from the surefire working directory")
                .exists();
        readmeText = Files.readString(L1_README);
    }

    // ---- slice 3 assertion 1: ICD presence + L1 linkage (meta) ------------

    @Test
    void forwarding_icd_exists_and_l1_readme_backlinks() {
        assertThat(icdText)
                .as("ICD-Agent-Bus-Forwarding must declare its ICD ID header")
                .contains("# ICD-Agent-Bus-Forwarding");
        assertThat(readmeText)
                .as("agent-bus L1 README must link the forwarding ICD (slice 3 back-link) — "
                  + "prevents the contract from drifting free of the L1 entry point")
                .contains("ICD-agent-bus-forwarding.md");
    }

    // ---- slice 3 assertion 2-3: tenantId + routeHandle mandatory ----------

    @Test
    void forwarding_envelope_requires_tenant_id() {
        assertThat(icdText)
                .as("ICD must declare a Forwarding Envelope Required Fields block (HD4)")
                .contains("Forwarding Envelope Required Fields")
                .as("ICD must state tenantId is mandatory on the forwarding envelope "
                  + "(HD3-003 / R-C.c — tenant continuity from registry key to forwarding)")
                .contains("`tenantId` 强制");
    }

    @Test
    void forwarding_envelope_requires_route_handle() {
        assertThat(icdText)
                .as("ICD must include routeHandle in the forwarding envelope required fields (HD4) "
                  + "so Stage 4 consumes the Stage 3 discovery result")
                .contains("routeHandle")
                .as("ICD must define a Route Handle block linking Stage 3 discovery to forwarding")
                .contains("Route Handle");
    }

    // ---- slice 3 assertion 4: payloadRef, no payload body -----------------

    @Test
    void forwarding_envelope_carries_payloadref_not_body() {
        assertThat(icdText)
                .as("ICD must define payloadRef as the envelope payload reference (HD4)")
                .contains("payloadRef")
                .as("ICD must forbid payload body on the forwarding envelope (control/data "
                  + "separation — large payloads take the data reference path, never the "
                  + "event/control channel)")
                .contains("不携带 payload body");
    }

    // ---- slice 3 assertion 5: failure modes form a verifiable set ----------

    @Test
    void forwarding_failure_modes_cover_backpressure_timeout_tenant() {
        assertThat(icdText)
                .as("ICD must express backpressure as an explicit failure mode")
                .contains("backpressure_rejected")
                .as("ICD must express delivery timeout as an explicit failure mode")
                .contains("delivery_timeout")
                .as("ICD must express tenant mismatch as an explicit failure mode (tenant "
                  + "isolation continuity from Stage 3 registry)")
                .contains("tenant_mismatch");
    }

    // ---- slice 3 assertion 6: no broker runtime in Stage 4 (ArchUnit) -----

    @Test
    void stage4_adds_no_broker_runtime_package() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.huawei.ascend.bus.spi");
        assertThat(classes)
                .as("sanity — ArchUnit must import agent-bus SPI classes from the test classpath")
                .isNotEmpty();

        Set<String> brokerRuntimePackages = classes.stream()
                .map(JavaClass::getPackageName)
                .filter(p -> p.contains(".broker") || p.contains(".queue")
                        || p.contains(".mailbox") || p.contains(".dlq")
                        || p.contains(".replay"))
                .collect(Collectors.toSet());
        assertThat(brokerRuntimePackages)
                .as("Stage 4 boundary: no broker / queue / mailbox / DLQ / replay runtime package "
                  + "may exist under com.huawei.ascend.bus.spi yet — ICD-Agent-Bus-Forwarding is "
                  + "design-level only (broker-agnostic). When Stage 5 admits a broker runtime, "
                  + "this assertion is the trip-wire that forces an explicit decision.")
                .isEmpty();
    }

    // ---- slice 3 assertion 7: discovery ↔ forwarding linked by route handle

    @Test
    void discovery_result_and_forwarding_envelope_linked_by_route_handle() {
        assertThat(icdText)
                .as("ICD must link Stage 3 discovery result to the forwarding envelope via route "
                  + "handle — prevents a target-less broker design and keeps forwarding consuming "
                  + "the Stage 3 route handle rather than a physical endpoint")
                .contains("通过 route handle 关联");
    }
}
