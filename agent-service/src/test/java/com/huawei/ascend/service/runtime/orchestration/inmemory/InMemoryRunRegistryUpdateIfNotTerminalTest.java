package com.huawei.ascend.service.runtime.orchestration.inmemory;

import com.huawei.ascend.engine.orchestration.spi.RunMode;
import com.huawei.ascend.service.runtime.runs.Run;
import com.huawei.ascend.service.runtime.runs.RunStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the atomic compare-and-set primitive that closes the
 * cancel-vs-complete lost-update race. Before this primitive, both
 * {@code RunController.cancel} and the orchestrator did
 * {@code findById → withStatus(validate against stale snapshot) → save (blind put)},
 * so a terminal state written by a parallel surface between the read and the save
 * was silently overwritten. {@code updateIfNotTerminal} performs the re-read,
 * terminal check, and write as one atomic step.
 */
class InMemoryRunRegistryUpdateIfNotTerminalTest {

    private static Run run(UUID id, RunStatus status) {
        return new Run(id, "tenant-A", "cap", status, RunMode.GRAPH,
                Instant.now(), Instant.now(), null, null, null, null, null);
    }

    @Test
    void terminal_run_is_not_mutated() {
        InMemoryRunRegistry runs = new InMemoryRunRegistry();
        UUID id = UUID.randomUUID();
        runs.save(run(id, RunStatus.SUCCEEDED));

        Optional<Run> result = runs.updateIfNotTerminal("tenant-A", id, r -> r.withStatus(RunStatus.CANCELLED));

        assertThat(result).isPresent();
        assertThat(result.get().status())
                .as("a terminal SUCCEEDED Run MUST NOT be overwritten with CANCELLED")
                .isEqualTo(RunStatus.SUCCEEDED);
        assertThat(runs.findById(id).orElseThrow().status()).isEqualTo(RunStatus.SUCCEEDED);
    }

    @Test
    void non_terminal_run_is_mutated() {
        InMemoryRunRegistry runs = new InMemoryRunRegistry();
        UUID id = UUID.randomUUID();
        runs.save(run(id, RunStatus.RUNNING));

        Optional<Run> result = runs.updateIfNotTerminal("tenant-A", id, r -> r.withStatus(RunStatus.CANCELLED));

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(RunStatus.CANCELLED);
        assertThat(runs.findById(id).orElseThrow().status()).isEqualTo(RunStatus.CANCELLED);
    }

    @Test
    void non_terminal_but_illegal_target_leaves_run_unchanged() {
        // FAILED is NON-terminal (FAILED -> RUNNING retry is legal) but FAILED -> CANCELLED
        // is illegal. updateIfNotTerminal must NOT propagate the IllegalStateException from
        // the state machine; it leaves the Run unchanged so the HTTP layer maps it to 409
        // illegal_state_transition rather than a 500. (rc36 regression guard.)
        InMemoryRunRegistry runs = new InMemoryRunRegistry();
        UUID id = UUID.randomUUID();
        runs.save(run(id, RunStatus.FAILED));

        Optional<Run> result = runs.updateIfNotTerminal("tenant-A", id, r -> r.withStatus(RunStatus.CANCELLED));

        assertThat(result).isPresent();
        assertThat(result.get().status())
                .as("FAILED -> CANCELLED is illegal; the Run must be left unchanged, not throw")
                .isEqualTo(RunStatus.FAILED);
        assertThat(runs.findById(id).orElseThrow().status()).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void absent_run_returns_empty() {
        InMemoryRunRegistry runs = new InMemoryRunRegistry();
        assertThat(runs.updateIfNotTerminal("tenant-A", UUID.randomUUID(), r -> r.withStatus(RunStatus.CANCELLED)))
                .isEmpty();
    }

    @Test
    void already_cancelled_run_stays_cancelled_idempotently() {
        InMemoryRunRegistry runs = new InMemoryRunRegistry();
        UUID id = UUID.randomUUID();
        runs.save(run(id, RunStatus.CANCELLED));

        Optional<Run> result = runs.updateIfNotTerminal("tenant-A", id, r -> r.withStatus(RunStatus.CANCELLED));

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(RunStatus.CANCELLED);
    }

    @Test
    void concurrent_terminal_writers_leave_exactly_one_winner() throws InterruptedException {
        // Two parallel surfaces race to finalize the same RUNNING Run: one to
        // SUCCEEDED, one to CANCELLED. Both transitions are legal from RUNNING, so
        // whichever lands first must win and the second must observe a terminal
        // state and become a no-op. The final status must never be torn.
        for (int trial = 0; trial < 200; trial++) {
            InMemoryRunRegistry runs = new InMemoryRunRegistry();
            UUID id = UUID.randomUUID();
            runs.save(run(id, RunStatus.RUNNING));

            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicInteger appliedCount = new AtomicInteger();
            Runnable succeed = transitionTask(runs, id, RunStatus.SUCCEEDED, barrier, appliedCount);
            Runnable cancel = transitionTask(runs, id, RunStatus.CANCELLED, barrier, appliedCount);

            Thread t1 = new Thread(succeed);
            Thread t2 = new Thread(cancel);
            t1.start();
            t2.start();
            t1.join(2_000);
            t2.join(2_000);

            RunStatus finalStatus = runs.findById(id).orElseThrow().status();
            assertThat(finalStatus)
                    .as("exactly one terminal writer must win the race (trial %d)", trial)
                    .isIn(RunStatus.SUCCEEDED, RunStatus.CANCELLED);
            assertThat(appliedCount.get())
                    .as("only one writer should have applied its mutation (trial %d)", trial)
                    .isEqualTo(1);
        }
    }

    private static Runnable transitionTask(InMemoryRunRegistry runs, UUID id, RunStatus target,
                                           CyclicBarrier barrier, AtomicInteger appliedCount) {
        return () -> {
            try {
                barrier.await(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                return;
            }
            runs.updateIfNotTerminal("tenant-A", id, r -> {
                appliedCount.incrementAndGet();
                return r.withStatus(target);
            });
        };
    }
}
