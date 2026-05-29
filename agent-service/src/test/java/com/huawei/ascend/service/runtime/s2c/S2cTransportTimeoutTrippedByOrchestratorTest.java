package com.huawei.ascend.service.runtime.s2c;

import com.huawei.ascend.engine.runtime.EngineRegistry;
import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryCheckpointer;
import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryRunRegistry;
import com.huawei.ascend.engine.exec.IterativeAgentLoopExecutor;
import com.huawei.ascend.engine.exec.SequentialGraphExecutor;
import com.huawei.ascend.service.runtime.orchestration.TestEnginePorts;
import com.huawei.ascend.service.runtime.orchestration.inmemory.SyncOrchestrator;
import com.huawei.ascend.service.runtime.runs.Run;
import com.huawei.ascend.service.runtime.runs.RunStatus;
import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;
import com.huawei.ascend.bus.spi.engine.SuspendSignal;
import com.huawei.ascend.bus.spi.s2c.S2cCallbackEnvelope;
import com.huawei.ascend.bus.spi.s2c.S2cCallbackResponse;
import com.huawei.ascend.bus.spi.s2c.S2cCallbackTransport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A transport whose returned CompletionStage never completes used to be able to
 * permanently pin an orchestrator worker on {@code toCompletableFuture().join()}.
 * The orchestrator now enforces an upper bound derived from
 * {@link S2cCallbackEnvelope#deadline()} (or a configurable fallback default)
 * and maps a timeout to a typed FAILED transition with reason
 * {@code s2c_timeout}.
 */
class S2cTransportTimeoutTrippedByOrchestratorTest {

    private static final String VALID_TRACE = "abcdef1234567890abcdef1234567890";

    @Test
    void transport_that_never_completes_trips_orchestrator_timeout_and_fails_the_run() {
        S2cCallbackTransport hangingTransport = new S2cCallbackTransport() {
            @Override
            public CompletionStage<S2cCallbackResponse> dispatch(S2cCallbackEnvelope envelope) {
                return new CompletableFuture<>(); // never completed by anyone
            }
        };

        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor())
                .registerS2cCallbackTransport(hangingTransport);

        InMemoryRunRegistry runs = new InMemoryRunRegistry();
        SyncOrchestrator orchestrator = TestEnginePorts.inProcessOrchestrator(
                runs, new InMemoryCheckpointer(), engines, Duration.ofMillis(150));

        AtomicReference<S2cCallbackEnvelope> captured = new AtomicReference<>();
        ExecutorDefinition.AgentLoopDefinition def = new ExecutorDefinition.AgentLoopDefinition(
                (ctx, payload, iter) -> {
                    if (captured.get() == null) {
                        S2cCallbackEnvelope env = new S2cCallbackEnvelope(
                                UUID.randomUUID(),
                                ctx.runId(),
                                "client.test.capability",
                                "request-payload",
                                VALID_TRACE,
                                UUID.randomUUID(),
                                null,
                                Map.of());
                        captured.set(env);
                        throw SuspendSignal.forClientCallback("loop-iter-0", env);
                    }
                    return ExecutorDefinition.ReasoningResult.done("never-reached");
                },
                5,
                Map.of());

        UUID runId = UUID.randomUUID();
        long started = System.nanoTime();
        assertThatThrownBy(() -> orchestrator.run(runId, "tenant-A", def, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("s2c_timeout");
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(elapsedMs)
                .as("orchestrator must give up well before the test wall-clock budget")
                .isLessThan(5_000);

        Run finalRun = runs.findById(runId).orElseThrow();
        assertThat(finalRun.status())
                .as("S2C timeout transitions the Run to FAILED")
                .isEqualTo(RunStatus.FAILED);
        assertThat(finalRun.finishedAt()).isNotNull();
    }

    /**
     * A misbehaving client can ship an envelope with a far-future absolute
     * deadline (e.g. now + 1h) intending to defeat the orchestrator's
     * per-call ceiling. The orchestrator MUST clamp the envelope deadline
     * against its own {@code s2cCallTimeout} so a hung transport still trips
     * the timeout inside the configured ceiling rather than pinning the
     * worker thread until the envelope deadline.
     */
    @Test
    void envelope_deadline_is_clamped_against_orchestrator_ceiling() {
        S2cCallbackTransport hangingTransport = new S2cCallbackTransport() {
            @Override
            public CompletionStage<S2cCallbackResponse> dispatch(S2cCallbackEnvelope envelope) {
                return new CompletableFuture<>();
            }
        };

        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor())
                .registerS2cCallbackTransport(hangingTransport);

        InMemoryRunRegistry runs = new InMemoryRunRegistry();
        // Ceiling = 150 ms; envelope below ships a 1-hour deadline.
        Duration ceiling = Duration.ofMillis(150);
        SyncOrchestrator orchestrator = TestEnginePorts.inProcessOrchestrator(
                runs, new InMemoryCheckpointer(), engines, ceiling);

        AtomicReference<S2cCallbackEnvelope> captured = new AtomicReference<>();
        ExecutorDefinition.AgentLoopDefinition def = new ExecutorDefinition.AgentLoopDefinition(
                (ctx, payload, iter) -> {
                    if (captured.get() == null) {
                        S2cCallbackEnvelope env = new S2cCallbackEnvelope(
                                UUID.randomUUID(),
                                ctx.runId(),
                                "client.test.capability",
                                "request-payload",
                                VALID_TRACE,
                                UUID.randomUUID(),
                                java.time.Instant.now().plus(Duration.ofHours(1)),
                                Map.of());
                        captured.set(env);
                        throw com.huawei.ascend.bus.spi.engine.SuspendSignal
                                .forClientCallback("loop-iter-0", env);
                    }
                    return ExecutorDefinition.ReasoningResult.done("never-reached");
                },
                5,
                Map.of());

        UUID runId = UUID.randomUUID();
        long started = System.nanoTime();
        assertThatThrownBy(() -> orchestrator.run(runId, "tenant-A", def, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("s2c_timeout");
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(elapsedMs)
                .as("orchestrator MUST trip the timeout near the configured ceiling, not the 1h envelope deadline")
                .isLessThan(5_000);
    }
}
