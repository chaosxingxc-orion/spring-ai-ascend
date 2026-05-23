package com.huawei.ascend.service.runtime.orchestration.inmemory;

import com.huawei.ascend.engine.runtime.EngineRegistry;
import com.huawei.ascend.engine.orchestration.spi.ExecutorDefinition;
import com.huawei.ascend.engine.orchestration.spi.RunMode;
import com.huawei.ascend.engine.orchestration.spi.SuspendSignal;
import com.huawei.ascend.service.runtime.runs.Run;
import com.huawei.ascend.service.runtime.runs.RunStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A cancel issued in parallel to a still-executing Run used to be silently
 * overwritten by the orchestrator's terminal SUCCEEDED/FAILED save, because
 * the local {@code run} record carried a stale {@code RUNNING} status and
 * {@code RunRepository.save} is a blind put. The orchestrator now re-reads
 * the Run from the repository before writing a terminal state and skips the
 * write when a parallel surface has already moved it to a terminal status.
 */
class SyncOrchestratorCancelRaceTest {

    @Test
    void cancel_during_execution_is_not_overwritten_by_succeeded() throws InterruptedException {
        InMemoryRunRegistry runs = new InMemoryRunRegistry();
        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor());
        SyncOrchestrator orchestrator = new SyncOrchestrator(
                runs, new InMemoryCheckpointer(), engines);

        CountDownLatch executorEntered = new CountDownLatch(1);
        CountDownLatch cancelApplied = new CountDownLatch(1);

        ExecutorDefinition.AgentLoopDefinition def = new ExecutorDefinition.AgentLoopDefinition(
                (ctx, payload, iter) -> {
                    executorEntered.countDown();
                    try {
                        cancelApplied.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    return ExecutorDefinition.ReasoningResult.done("would-be-succeeded");
                },
                1,
                Map.of());

        UUID runId = UUID.randomUUID();
        Thread orch = new Thread(() -> orchestrator.run(runId, "tenant-A", def, null),
                "orchestrator-under-test");
        orch.setDaemon(true);
        orch.start();

        assertThat(executorEntered.await(5, TimeUnit.SECONDS))
                .as("executor lambda should reach the latch")
                .isTrue();

        // Simulate RunController.cancel landing on a parallel HTTP worker.
        Run current = runs.findById(runId).orElseThrow();
        runs.save(current.withStatus(RunStatus.CANCELLED).withFinishedAt(Instant.now()));
        cancelApplied.countDown();

        orch.join(5_000);
        assertThat(orch.isAlive()).as("orchestrator thread should have returned").isFalse();

        Run finalRun = runs.findById(runId).orElseThrow();
        assertThat(finalRun.status())
                .as("a parallel CANCELLED state MUST NOT be overwritten by SUCCEEDED")
                .isEqualTo(RunStatus.CANCELLED);
    }

    /**
     * Companion to the SUCCEEDED-overwrite case: the orchestrator's non-terminal
     * SUSPENDED save was also a blind put on a possibly-stale local snapshot,
     * so a cancel landing between dispatch and the suspension-save would be
     * silently overwritten with SUSPENDED. The {@code saveIfNotTerminal} helper
     * now re-reads before every non-terminal save and short-circuits the
     * orchestrator loop when the persisted row is already terminal.
     */
    @Test
    void cancel_during_executor_suspension_is_not_overwritten_by_suspended() throws InterruptedException {
        InMemoryRunRegistry runs = new InMemoryRunRegistry();
        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor());
        SyncOrchestrator orchestrator = new SyncOrchestrator(
                runs, new InMemoryCheckpointer(), engines);

        CountDownLatch executorEntered = new CountDownLatch(1);
        CountDownLatch cancelApplied = new CountDownLatch(1);

        // The executor blocks until cancel lands, then raises a child-suspend
        // SuspendSignal. Without the saveIfNotTerminal guard the orchestrator
        // would then save SUSPENDED on top of the just-written CANCELLED row.
        ExecutorDefinition.AgentLoopDefinition childDef =
                new ExecutorDefinition.AgentLoopDefinition(
                        (ctx, payload, iter) -> ExecutorDefinition.ReasoningResult.done("never-reached"),
                        1,
                        Map.of());
        ExecutorDefinition.AgentLoopDefinition def = new ExecutorDefinition.AgentLoopDefinition(
                (ctx, payload, iter) -> {
                    executorEntered.countDown();
                    try {
                        cancelApplied.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    throw new SuspendSignal("loop-iter-0", "resume-payload",
                            RunMode.AGENT_LOOP, childDef);
                },
                1,
                Map.of());

        UUID runId = UUID.randomUUID();
        Thread orch = new Thread(() -> orchestrator.run(runId, "tenant-A", def, null),
                "orchestrator-under-suspension-cancel");
        orch.setDaemon(true);
        orch.start();

        assertThat(executorEntered.await(5, TimeUnit.SECONDS))
                .as("executor lambda should reach the latch")
                .isTrue();

        Run current = runs.findById(runId).orElseThrow();
        runs.save(current.withStatus(RunStatus.CANCELLED).withFinishedAt(Instant.now()));
        cancelApplied.countDown();

        orch.join(5_000);
        assertThat(orch.isAlive()).as("orchestrator thread should have returned").isFalse();

        Run finalRun = runs.findById(runId).orElseThrow();
        assertThat(finalRun.status())
                .as("a parallel CANCELLED state MUST NOT be overwritten by SUSPENDED")
                .isEqualTo(RunStatus.CANCELLED);
    }
}
