package ascend.springai.service.runtime.runs;

import ascend.springai.engine.orchestration.spi.RunMode;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RunTest {

    @Test
    void run_requires_tenantId() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Run(UUID.randomUUID(), null, "cap", RunStatus.PENDING,
                RunMode.GRAPH, Instant.now(), null, null, null, null, null, null))
            .withMessageContaining("tenantId");
    }

    @Test
    void run_requires_runId() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Run(null, "tenant-1", "cap", RunStatus.PENDING,
                RunMode.GRAPH, Instant.now(), null, null, null, null, null, null))
            .withMessageContaining("runId");
    }

    @Test
    void run_requires_capabilityName() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Run(UUID.randomUUID(), "tenant-1", null, RunStatus.PENDING,
                RunMode.GRAPH, Instant.now(), null, null, null, null, null, null))
            .withMessageContaining("capabilityName");
    }

    @Test
    void run_requires_mode() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Run(UUID.randomUUID(), "tenant-1", "cap", RunStatus.PENDING,
                null, Instant.now(), null, null, null, null, null, null))
            .withMessageContaining("mode");
    }

    @Test
    void run_valid_construction_carries_mandatory_fields() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        var run = new Run(id, "tenant-1", "llm-call", RunStatus.PENDING,
            RunMode.AGENT_LOOP, now, null, null, null, 1, null, null);
        assertThat(run.runId()).isEqualTo(id);
        assertThat(run.tenantId()).isEqualTo("tenant-1");
        assertThat(run.status()).isEqualTo(RunStatus.PENDING);
        assertThat(run.mode()).isEqualTo(RunMode.AGENT_LOOP);
    }

    @Test
    void run_allows_nullable_suspension_fields() {
        UUID parentId = UUID.randomUUID();
        Instant now = Instant.now();
        var run = new Run(UUID.randomUUID(), "tenant-1", "step-3", RunStatus.SUSPENDED,
            RunMode.GRAPH, now, now, null, parentId, null, "node-2", now);
        assertThat(run.status()).isEqualTo(RunStatus.SUSPENDED);
        assertThat(run.parentRunId()).isEqualTo(parentId);
        assertThat(run.parentNodeKey()).isEqualTo("node-2");
        assertThat(run.suspendedAt()).isEqualTo(now);
    }
}
