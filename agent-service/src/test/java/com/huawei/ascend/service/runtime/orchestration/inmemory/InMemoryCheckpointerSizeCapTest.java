package com.huawei.ascend.service.runtime.orchestration.inmemory;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Root-cause: §4 #13 specifies a 16-KiB inline cap for Checkpointer payloads, but
 * InMemoryCheckpointer had no enforcement. Oversize payloads silently bloated in-memory
 * state and would violate the contract when W2 Postgres checkpointer enforces it (HD-A.10).
 * Fix: posture-aware precommit check — dev posture warns; research/prod posture throws.
 *
 * <p>TCK-promotion-candidate: when agent-runtime-tck is created on Rule R-D.a.b trigger,
 * this class lifts-and-shifts as the Checkpointer payload-cap conformance test.
 * The dev/research/prod posture branching MUST be honoured by any alternative impl.
 * See docs/CLAUDE-deferred.md Rule R-D.a.b "Pre-promotion holding tank".
 */
class InMemoryCheckpointerSizeCapTest {

    private static final int ONE_BYTE_OVER = InMemoryCheckpointer.MAX_INLINE_PAYLOAD_BYTES + 1;
    private static final int EXACTLY_AT_CAP = InMemoryCheckpointer.MAX_INLINE_PAYLOAD_BYTES;

    @Test
    void dev_posture_construction_delegates_to_posture_gate() {
        // AppPostureGate.requireDevForInMemoryComponent called; dev posture warns, does not throw.
        assertThatCode(InMemoryCheckpointer::new).doesNotThrowAnyException();
    }

    @Test
    void dev_posture_permits_small_payload() {
        InMemoryCheckpointer cp = new InMemoryCheckpointer(); // APP_POSTURE not set in tests → dev
        byte[] payload = new byte[1024];
        UUID runId = UUID.randomUUID();
        cp.save(runId, "key", payload);
        assertThat(cp.load(runId, "key")).isPresent();
    }

    @Test
    void dev_posture_warns_but_saves_oversize_payload() {
        InMemoryCheckpointer cp = new InMemoryCheckpointer();
        byte[] oversize = new byte[ONE_BYTE_OVER];
        UUID runId = UUID.randomUUID();
        // Should not throw — dev posture just warns via stderr
        cp.save(runId, "big-key", oversize);
        // Payload is still stored (in-memory non-durable; warn and continue)
        assertThat(cp.load(runId, "big-key")).isPresent()
                .hasValueSatisfying(b -> assertThat(b.length).isEqualTo(ONE_BYTE_OVER));
    }

    @Test
    void dev_posture_allows_payload_exactly_at_cap() {
        InMemoryCheckpointer cp = new InMemoryCheckpointer();
        byte[] atCap = new byte[EXACTLY_AT_CAP];
        UUID runId = UUID.randomUUID();
        cp.save(runId, "at-cap", atCap); // exactly at limit — no warn, no throw
        assertThat(cp.load(runId, "at-cap")).isPresent();
    }

    /**
     * Tests research/prod posture by constructing InMemoryCheckpointer with posture env var.
     * Since we cannot set env vars in-process on JVM, we verify the failOnOversize path via
     * the package-private constructor that accepts the flag directly. This preserves test
     * isolation without requiring process-level env manipulation.
     */
    @Test
    void research_prod_posture_throws_on_oversize_payload() {
        InMemoryCheckpointer cp = new InMemoryCheckpointer(true); // research/prod: failOnOversize=true
        byte[] oversize = new byte[ONE_BYTE_OVER];
        UUID runId = UUID.randomUUID();
        assertThatThrownBy(() -> cp.save(runId, "key", oversize))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("16 KiB")
                .hasMessageContaining("payload_store_spi");
    }

    @Test
    void research_prod_posture_allows_payload_at_cap() {
        InMemoryCheckpointer cp = new InMemoryCheckpointer(true);
        byte[] atCap = new byte[EXACTLY_AT_CAP];
        UUID runId = UUID.randomUUID();
        cp.save(runId, "at-cap", atCap); // exactly at limit — no throw
        assertThat(cp.load(runId, "at-cap")).isPresent();
    }
}
