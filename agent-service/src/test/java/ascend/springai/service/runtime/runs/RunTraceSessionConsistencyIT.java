package ascend.springai.service.runtime.runs;

import ascend.springai.engine.orchestration.spi.RunMode;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces ARCHITECTURE.md §4 #54 (Trace ↔ Run ↔ Session identity) for the Run entity.
 *
 * <p>L1.x contract:
 * <ul>
 *   <li>{@code Run.traceId} is nullable but, when populated, MUST be a 32-character
 *       lowercase hex string (16-byte W3C trace id).</li>
 *   <li>{@code Run.sessionId} is nullable; multiple Runs MAY share the same value.</li>
 *   <li>The backward-compatible 12-arg constructor populates both as null so existing
 *       callers compile unchanged (ADR-0062 reversal-cost discussion).</li>
 *   <li>{@code withTraceId} / {@code withSessionId} preserve all other fields.</li>
 *   <li>{@code withStatus} / {@code withFinishedAt} / {@code withUpdatedAt} /
 *       {@code withSuspension} propagate traceId and sessionId unchanged.</li>
 * </ul>
 *
 * <p>Enforcer E40. ADR-0062.
 */
class RunTraceSessionConsistencyIT {

    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String SESSION_ID = "session-abc-123";

    @Test
    void canonical_constructor_accepts_traceId_and_sessionId() {
        Run run = new Run(UUID.randomUUID(), "tenant-1", "cap", RunStatus.PENDING,
                RunMode.GRAPH, Instant.now(), null, null, null, null, null, null,
                TRACE_ID, SESSION_ID);
        assertThat(run.traceId()).isEqualTo(TRACE_ID);
        assertThat(run.sessionId()).isEqualTo(SESSION_ID);
    }

    @Test
    void backward_compatible_12_arg_constructor_sets_trace_and_session_to_null() {
        Run run = new Run(UUID.randomUUID(), "tenant-1", "cap", RunStatus.PENDING,
                RunMode.GRAPH, Instant.now(), null, null, null, null, null, null);
        assertThat(run.traceId()).as("L1.x nullable per §4 #54").isNull();
        assertThat(run.sessionId()).as("L1.x nullable per §4 #54").isNull();
    }

    @Test
    void with_status_preserves_traceId_and_sessionId() {
        Run run = new Run(UUID.randomUUID(), "tenant-1", "cap", RunStatus.PENDING,
                RunMode.GRAPH, Instant.now(), null, null, null, null, null, null,
                TRACE_ID, SESSION_ID);
        Run running = run.withStatus(RunStatus.RUNNING);
        assertThat(running.traceId()).isEqualTo(TRACE_ID);
        assertThat(running.sessionId()).isEqualTo(SESSION_ID);
        assertThat(running.status()).isEqualTo(RunStatus.RUNNING);
    }

    @Test
    void with_suspension_preserves_traceId_and_sessionId() {
        Run run = new Run(UUID.randomUUID(), "tenant-1", "cap", RunStatus.RUNNING,
                RunMode.AGENT_LOOP, Instant.now(), null, null, null, null, null, null,
                TRACE_ID, SESSION_ID);
        Run suspended = run.withSuspension("node-2", Instant.now());
        assertThat(suspended.traceId()).isEqualTo(TRACE_ID);
        assertThat(suspended.sessionId()).isEqualTo(SESSION_ID);
        assertThat(suspended.status()).isEqualTo(RunStatus.SUSPENDED);
    }

    @Test
    void with_traceId_replaces_only_the_trace_field() {
        Run run = new Run(UUID.randomUUID(), "tenant-1", "cap", RunStatus.PENDING,
                RunMode.GRAPH, Instant.now(), null, null, null, null, null, null,
                null, SESSION_ID);
        Run withTrace = run.withTraceId(TRACE_ID);
        assertThat(withTrace.traceId()).isEqualTo(TRACE_ID);
        assertThat(withTrace.sessionId()).isEqualTo(SESSION_ID);
        assertThat(withTrace.status()).isEqualTo(RunStatus.PENDING);
    }

    @Test
    void with_sessionId_replaces_only_the_session_field() {
        Run run = new Run(UUID.randomUUID(), "tenant-1", "cap", RunStatus.PENDING,
                RunMode.GRAPH, Instant.now(), null, null, null, null, null, null,
                TRACE_ID, null);
        Run withSession = run.withSessionId(SESSION_ID);
        assertThat(withSession.sessionId()).isEqualTo(SESSION_ID);
        assertThat(withSession.traceId()).isEqualTo(TRACE_ID);
    }

    @Test
    void traceId_when_populated_is_32_lowercase_hex() {
        Run run = new Run(UUID.randomUUID(), "tenant-1", "cap", RunStatus.PENDING,
                RunMode.GRAPH, Instant.now(), null, null, null, null, null, null,
                TRACE_ID, null);
        assertThat(run.traceId()).matches("[0-9a-f]{32}");
    }
}
