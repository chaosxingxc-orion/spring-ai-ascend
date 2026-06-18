package com.huawei.ascend.collab.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.collab.core.WorkResult.Status;
import com.huawei.ascend.collab.sim.ScriptedWorker;
import com.huawei.ascend.collab.sim.ScriptedWorker.Behavior;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Backpressure & token-economy hardening of {@link Coordinator}: retry backoff,
 * per-batch dispatch budget, and result dedupe. Backoff is verified through an
 * injected {@link Coordinator.Sleeper} so the test asserts the policy without
 * sleeping for real.
 */
class CoordinatorHardeningTest {

    private static final java.util.function.LongSupplier CLOCK = () -> 0L;

    /** A sleeper that records requested waits instead of blocking. */
    private static final class RecordingSleeper implements Coordinator.Sleeper {
        final List<Long> waits = new ArrayList<>();

        @Override
        public void sleep(long ms) {
            waits.add(ms);
        }
    }

    @Test
    void backoffIsAppliedBetweenReclaimRetriesWithGrowingDelay() {
        // A worker that fails the first 2 attempts then succeeds → 2 reclaims → 2 backoff waits.
        ScriptedWorker w = ScriptedWorker.flaky("w", "cap", 2);
        RecordingSleeper sleeper = new RecordingSleeper();
        CoordinatorConfig cfg = CoordinatorConfig.defaults()
                .withBackoff(CoordinatorConfig.exponentialBackoff(100, 5_000));
        Coordinator c = new Coordinator(List.of(w), ResultValidator.nonEmptyOutput(),
                "t", CLOCK, CollaborationObserver.NOOP, cfg, sleeper);

        CollaborationResult r = c.run(List.of(SubTask.of("t1", "cap", "p")));

        assertEquals(Status.COMPLETED, r.outcomes().get("t1"), "flaky task converges");
        assertEquals(List.of(100L, 200L), sleeper.waits, "exponential backoff applied per reclaim");
    }

    @Test
    void noBackoffByDefault() {
        ScriptedWorker w = ScriptedWorker.flaky("w", "cap", 1);
        RecordingSleeper sleeper = new RecordingSleeper();
        Coordinator c = new Coordinator(List.of(w), ResultValidator.nonEmptyOutput(),
                "t", CLOCK, CollaborationObserver.NOOP, CoordinatorConfig.defaults(), sleeper);

        c.run(List.of(SubTask.of("t1", "cap", "p")));

        assertTrue(sleeper.waits.isEmpty(), "default config never sleeps");
    }

    @Test
    void dispatchBudgetFailsLaterTasksFastInsteadOfBurningAttempts() {
        // Always-failing worker; each task would otherwise burn maxAttempts(=2) dispatches.
        // Budget of 3 dispatches: task1 uses 2, task2 gets 1 then is cut off → still fails,
        // but the batch spent 3 dispatches total, not 4.
        ScriptedWorker w = ScriptedWorker.of("w", "cap", Behavior.FAIL);
        CoordinatorConfig cfg = CoordinatorConfig.defaults().withMaxDispatches(3);
        Coordinator c = new Coordinator(List.of(w), ResultValidator.nonEmptyOutput(),
                "t", CLOCK, CollaborationObserver.NOOP, cfg);

        CollaborationResult r = c.run(List.of(
                SubTask.of("t1", "cap", "a"),
                SubTask.of("t2", "cap", "b")));

        assertEquals(Status.FAILED, r.outcomes().get("t1"));
        assertEquals(Status.FAILED, r.outcomes().get("t2"));
        assertEquals(3, w.calls(), "batch dispatch budget capped total dispatches at 3, not 4");
        assertTrue(r.log().stream().anyMatch(e -> "dispatch budget exhausted".equals(e.detail())),
                "the budget cutoff is recorded");
    }

    @Test
    void dedupeReusesACompletedResultForIdenticalWork() {
        ScriptedWorker w = ScriptedWorker.of("w", "cap", Behavior.COMPLETE);
        CoordinatorConfig cfg = CoordinatorConfig.defaults().withDedupe(true);
        Coordinator c = new Coordinator(List.of(w), ResultValidator.nonEmptyOutput(),
                "t", CLOCK, CollaborationObserver.NOOP, cfg);

        // Same capability + payload, different ids → second is served from cache, no dispatch.
        CollaborationResult r = c.run(List.of(
                SubTask.of("t1", "cap", "same-payload"),
                SubTask.of("t2", "cap", "same-payload")));

        assertEquals(Status.COMPLETED, r.outcomes().get("t1"));
        assertEquals(Status.COMPLETED, r.outcomes().get("t2"));
        assertEquals(1, w.calls(), "identical work dispatched once; the duplicate is deduped (token saved)");
        assertTrue(r.log().stream().anyMatch(e -> "deduped: token saved".equals(e.detail())));
    }

    @Test
    void dedupeOffByDefaultStillDispatchesEachTask() {
        ScriptedWorker w = ScriptedWorker.of("w", "cap", Behavior.COMPLETE);
        Coordinator c = new Coordinator(List.of(w));

        c.run(List.of(
                SubTask.of("t1", "cap", "same"),
                SubTask.of("t2", "cap", "same")));

        assertEquals(2, w.calls(), "default config does not dedupe");
    }

    @Test
    void boundedConcurrencyStillCompletesEveryTask() {
        ScriptedWorker w = ScriptedWorker.of("w", "cap", Behavior.COMPLETE);
        CoordinatorConfig cfg = CoordinatorConfig.defaults().withMaxConcurrency(2);
        Coordinator c = new Coordinator(List.of(w), ResultValidator.nonEmptyOutput(),
                "t", CLOCK, CollaborationObserver.NOOP, cfg);

        List<SubTask> tasks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            tasks.add(SubTask.of("t" + i, "cap", "p" + i));
        }
        CollaborationResult r = c.runConcurrent(tasks, 16);

        assertEquals(50, r.count(Status.COMPLETED), "all tasks complete under a bounded pool");
        assertFalse(r.outcomes().containsValue(Status.FAILED));
    }
}
