package ascend.springai.service.runtime.orchestration.inmemory;

import ascend.springai.service.runtime.orchestration.NoopTraceContext;
import ascend.springai.engine.orchestration.spi.ExecutorDefinition;
import ascend.springai.engine.orchestration.spi.RunContext;
import ascend.springai.engine.orchestration.spi.SuspendSignal;
import ascend.springai.engine.orchestration.spi.TraceContext;
import ascend.springai.engine.orchestration.spi.RunMode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Root-cause: IterativeAgentLoopExecutor:39 used "savedState + resumePayload" — implicit
 * Object.toString() on a non-String resumePayload produces an unrecoverable byte stream.
 * Root cause: Object + String → compiler calls resumePayload.toString(), which for any
 * non-String type yields e.g. "[Ljava.lang.Object;@1234abcd". Persisted via
 * payload.toString().getBytes(), this stream is unrecoverable on resume (HD-A.8).
 * Fix: explicit instanceof String check at both the save and resume paths; fail-fast ISE
 * with a reference to ADR-0022 (typed payload, W2). This test verifies the fix.
 */
class IterativeAgentLoopExecutorResumeCursorTest {

    private final InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
    private final IterativeAgentLoopExecutor executor = new IterativeAgentLoopExecutor();

    /** Fake RunContext backed by the shared InMemoryCheckpointer. */
    private RunContext ctx(UUID runId) {
        final TraceContext trace = NoopTraceContext.newRoot();
        return new RunContext() {
            @Override public UUID runId() { return runId; }
            @Override public String tenantId() { return "test-tenant"; }
            @Override public ascend.springai.engine.orchestration.spi.Checkpointer checkpointer() { return checkpointer; }
            @Override public String traceId() { return trace.traceId(); }
            @Override public String spanId() { return trace.spanId(); }
            @Override public String sessionId() { return trace.sessionId(); }
            @Override public TraceContext traceContext() { return trace; }
            @Override public Object suspendForChild(String parentNodeKey, RunMode childMode,
                                                     ExecutorDefinition childDef, Object resumePayload)
                    throws SuspendSignal {
                throw new SuspendSignal(parentNodeKey, resumePayload, childMode, childDef);
            }
        };
    }

    @Test
    void string_payload_resume_completes_successfully() throws SuspendSignal {
        UUID runId = UUID.randomUUID();
        RunContext ctx = ctx(runId);

        // A two-iteration reasoner: iter 0 returns "hello|", iter 1 returns terminal "hello|world"
        ExecutorDefinition.AgentLoopDefinition def = new ExecutorDefinition.AgentLoopDefinition(
                (c, payload, iter) -> {
                    if (iter == 0) return ExecutorDefinition.ReasoningResult.next("hello|");
                    return ExecutorDefinition.ReasoningResult.done(payload.toString() + "world");
                },
                5,
                Map.of()
        );

        Object result = executor.execute(ctx, def, null);
        assertThat(result).isEqualTo("hello|world");
    }

    @Test
    void string_payload_suspend_and_resume_works_correctly() throws SuspendSignal {
        UUID runId = UUID.randomUUID();

        // Iter 0: run suspend (throws SuspendSignal) so we can simulate resume
        // Iter 0 resumes with "CHILD-RESULT"; iter 0 sees it and returns terminal
        // The test simulates the orchestrator's suspend → child run → resume cycle manually.
        final boolean[] suspended = {false};

        ExecutorDefinition.AgentLoopDefinition def = new ExecutorDefinition.AgentLoopDefinition(
                (c, payload, iter) -> {
                    if (iter == 0 && !suspended[0]) {
                        suspended[0] = true;
                        c.suspendForChild("iter0", RunMode.AGENT_LOOP,
                                new ExecutorDefinition.AgentLoopDefinition((cc, p, i) ->
                                        ExecutorDefinition.ReasoningResult.done("CHILD"), 1, Map.of()),
                                null);
                        return null; // unreachable
                    }
                    return ExecutorDefinition.ReasoningResult.done(
                            (payload == null ? "" : payload.toString()) + "|resumed");
                },
                5,
                Map.of()
        );

        RunContext ctx1 = ctx(runId);
        // First call — expect suspend
        try {
            executor.execute(ctx1, def, null);
        } catch (SuspendSignal signal) {
            // expected
        }

        // Resume call — resumePayload is a String
        RunContext ctx2 = ctx(runId);
        Object result = executor.execute(ctx2, def, "CHILD-RESULT");
        assertThat(result.toString()).isEqualTo("CHILD-RESULT|resumed");
    }

    @Test
    void non_string_resume_payload_with_existing_state_throws_illegal_state() {
        UUID runId = UUID.randomUUID();

        // Manually plant a RESUME_STATE_KEY in the checkpointer to simulate existing saved state
        checkpointer.save(runId, IterativeAgentLoopExecutor.RESUME_STATE_KEY,
                "accumulated-state|".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        checkpointer.save(runId, "_loop_resume_iter",
                "1".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        ExecutorDefinition.AgentLoopDefinition def = new ExecutorDefinition.AgentLoopDefinition(
                (c, payload, iter) -> ExecutorDefinition.ReasoningResult.done(payload),
                5,
                Map.of()
        );

        RunContext ctx = ctx(runId);
        // resumePayload is an Integer (non-String) — with existing saved state, this must throw
        assertThatThrownBy(() -> executor.execute(ctx, def, Integer.valueOf(42)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("String resumePayload")
                .hasMessageContaining("ADR-0022");
    }

    @Test
    void null_saved_state_accepts_non_string_initial_payload() throws SuspendSignal {
        UUID runId = UUID.randomUUID();
        RunContext ctx = ctx(runId);

        // No saved state — resumePayload can be any Object (first-call initial payload)
        ExecutorDefinition.AgentLoopDefinition def = new ExecutorDefinition.AgentLoopDefinition(
                (c, payload, iter) -> ExecutorDefinition.ReasoningResult.done(payload),
                5,
                Map.of()
        );

        // Integer as initial payload — no saved state, so no exception expected
        Object result = executor.execute(ctx, def, Integer.valueOf(99));
        assertThat(result).isEqualTo(99);
    }
}
