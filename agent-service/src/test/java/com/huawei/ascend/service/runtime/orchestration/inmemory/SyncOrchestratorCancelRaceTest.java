package com.huawei.ascend.service.runtime.orchestration.inmemory;

import com.huawei.ascend.engine.runtime.EngineRegistry;
import com.huawei.ascend.engine.orchestration.spi.ExecutorDefinition;
import com.huawei.ascend.engine.orchestration.spi.RunMode;
import com.huawei.ascend.engine.orchestration.spi.SuspendSignal;
import com.huawei.ascend.service.runtime.runs.Run;
import com.huawei.ascend.service.runtime.runs.RunStatus;
import com.huawei.ascend.service.runtime.runs.spi.RunRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

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

    /**
     * The two tests above land the cancel <em>before</em> the orchestrator reaches
     * its terminal-transition helper, so the helper's re-read already observes
     * CANCELLED. They therefore never exercise the actual read-modify-write window:
     * a cancel interleaving <em>between</em> the helper's re-read and its write.
     *
     * <p>This test reproduces that window deterministically and single-threaded via
     * a repository decorator that, the moment the orchestrator attempts to transition
     * the RUNNING Run, writes CANCELLED into the backing store and hands the caller
     * the stale RUNNING snapshot. A non-atomic {@code findById}-then-{@code save}
     * helper validates SUCCEEDED against the stale snapshot and blind-overwrites
     * CANCELLED (cancel lost). Routing the transition through the atomic
     * {@link RunRepository#updateIfNotTerminal} compare-and-set instead makes the
     * re-read+check+write a single step, so the interleaved CANCELLED survives.
     */
    @Test
    void terminal_transition_uses_atomic_cas_so_an_interleaved_cancel_survives() {
        InMemoryRunRegistry backing = new InMemoryRunRegistry();
        CancelInjectingRepository runs = new CancelInjectingRepository(backing);
        EngineRegistry engines = new EngineRegistry()
                .register(new SequentialGraphExecutor())
                .register(new IterativeAgentLoopExecutor());
        SyncOrchestrator orchestrator = new SyncOrchestrator(
                runs, new InMemoryCheckpointer(), engines);

        ExecutorDefinition.AgentLoopDefinition def = new ExecutorDefinition.AgentLoopDefinition(
                (ctx, payload, iter) -> ExecutorDefinition.ReasoningResult.done("would-be-succeeded"),
                1,
                Map.of());

        UUID runId = UUID.randomUUID();
        // Inject the cancel on the first transition attempted against a RUNNING Run
        // (i.e. the post-dispatch SUCCEEDED write), not the PENDING -> RUNNING entry.
        runs.armCancelInjectionOnRunningTransition();
        orchestrator.run(runId, "tenant-A", def, null);

        assertThat(backing.findById(runId).orElseThrow().status())
                .as("a cancel landing inside the read-modify-write window MUST NOT be overwritten by SUCCEEDED")
                .isEqualTo(RunStatus.CANCELLED);
    }

    /**
     * Test double that simulates a concurrent {@code RunController.cancel} landing in
     * the orchestrator's status-transition window. One-shot: on the first
     * {@code findById}/{@code updateIfNotTerminal} that observes a RUNNING Run while
     * armed, it writes CANCELLED into the delegate before yielding control.
     */
    private static final class CancelInjectingRepository implements RunRepository {
        private final InMemoryRunRegistry delegate;
        private boolean armed;

        CancelInjectingRepository(InMemoryRunRegistry delegate) {
            this.delegate = delegate;
        }

        void armCancelInjectionOnRunningTransition() {
            this.armed = true;
        }

        /**
         * If armed and the persisted Run is RUNNING, write CANCELLED into the delegate
         * and return the pre-cancel (stale RUNNING) snapshot; otherwise empty. One-shot.
         */
        private Optional<Run> injectCancelIfRunning(UUID runId) {
            Optional<Run> before = delegate.findById(runId);
            if (armed && before.isPresent() && before.get().status() == RunStatus.RUNNING) {
                armed = false;
                delegate.save(before.get().withStatus(RunStatus.CANCELLED).withFinishedAt(Instant.now()));
                return before;
            }
            return Optional.empty();
        }

        @Override
        public Optional<Run> findById(UUID runId) {
            Optional<Run> stale = injectCancelIfRunning(runId);
            return stale.isPresent() ? stale : delegate.findById(runId);
        }

        @Override
        public Optional<Run> updateIfNotTerminal(UUID runId, UnaryOperator<Run> mutator) {
            injectCancelIfRunning(runId);
            return delegate.updateIfNotTerminal(runId, mutator);
        }

        @Override
        public Run save(Run run) {
            return delegate.save(run);
        }

        @Override
        public List<Run> findByTenant(String tenantId) {
            return delegate.findByTenant(tenantId);
        }

        @Override
        public List<Run> findByParentRunId(UUID parentRunId) {
            return delegate.findByParentRunId(parentRunId);
        }

        @Override
        public List<Run> findByTenantAndStatus(String tenantId, RunStatus status) {
            return delegate.findByTenantAndStatus(tenantId, status);
        }

        @Override
        public List<Run> findRootRuns(String tenantId) {
            return delegate.findRootRuns(tenantId);
        }
    }
}
