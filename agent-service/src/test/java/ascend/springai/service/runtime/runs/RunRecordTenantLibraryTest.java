package ascend.springai.service.runtime.runs;

import ascend.springai.engine.orchestration.spi.RunMode;

import ascend.springai.service.runtime.idempotency.IdempotencyRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Library-mode SPI conformance test for the Rule R-C.c contract-spine — every
 * persistent record under
 * {@code agent-service/src/main/java/ascend/springai/service/runtime/{runs,idempotency}/**}
 * MUST require a non-null tenantId via Objects.requireNonNull in its compact
 * constructor. Path scope relocated from the dissolved agent-runtime-core
 * module per ADR-0088 (rc13).
 *
 * <p>This class is part of the Rule D-3.b evidence layer + the Rule R-D.a.b
 * TCK-promotion holding tank. Pure JUnit Jupiter — no Spring, no I/O.
 */
class RunRecordTenantLibraryTest {

    @Test
    void run_constructor_rejects_null_tenant_id() {
        assertThatThrownBy(() -> new Run(
                UUID.randomUUID(),
                null,
                "capability",
                RunStatus.PENDING,
                RunMode.GRAPH,
                Instant.now(),
                null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId is required");
    }

    @Test
    void run_constructor_rejects_null_run_id() {
        assertThatThrownBy(() -> new Run(
                null,
                "tenant-1",
                "capability",
                RunStatus.PENDING,
                RunMode.GRAPH,
                Instant.now(),
                null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("runId is required");
    }

    @Test
    void run_constructor_accepts_minimal_valid_input() {
        Run r = new Run(
                UUID.randomUUID(),
                "tenant-1",
                "capability",
                RunStatus.PENDING,
                RunMode.GRAPH,
                Instant.now(),
                null, null, null, null, null, null);
        assertThat(r.tenantId()).isEqualTo("tenant-1");
        assertThat(r.status()).isEqualTo(RunStatus.PENDING);
        assertThat(r.mode()).isEqualTo(RunMode.GRAPH);
        // Telemetry-vertical fields default to null in the back-compat constructor
        assertThat(r.traceId()).isNull();
        assertThat(r.sessionId()).isNull();
    }

    @Test
    void run_with_status_validates_dfa_before_constructing_updated_record() {
        Run pending = new Run(
                UUID.randomUUID(),
                "tenant-1",
                "capability",
                RunStatus.PENDING,
                RunMode.GRAPH,
                Instant.now(),
                null, null, null, null, null, null);

        // Legal: PENDING -> RUNNING
        Run running = pending.withStatus(RunStatus.RUNNING);
        assertThat(running.status()).isEqualTo(RunStatus.RUNNING);

        // Illegal: PENDING -> SUCCEEDED
        assertThatThrownBy(() -> pending.withStatus(RunStatus.SUCCEEDED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void idempotency_record_constructor_rejects_null_tenant_id() {
        assertThatThrownBy(() -> new IdempotencyRecord(
                "key-1",
                null,
                UUID.randomUUID(),
                Instant.now()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId is required");
    }

    @Test
    void idempotency_record_constructor_rejects_null_idempotency_key() {
        assertThatThrownBy(() -> new IdempotencyRecord(
                null,
                "tenant-1",
                UUID.randomUUID(),
                Instant.now()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("idempotencyKey is required");
    }

    @Test
    void idempotency_record_accepts_null_run_id_and_claimed_at() {
        IdempotencyRecord r = new IdempotencyRecord(
                "key-1", "tenant-1", null, null);
        assertThat(r.idempotencyKey()).isEqualTo("key-1");
        assertThat(r.tenantId()).isEqualTo("tenant-1");
        assertThat(r.runId()).isNull();
        assertThat(r.claimedAt()).isNull();
    }
}
