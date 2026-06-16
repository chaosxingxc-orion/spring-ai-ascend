package com.huawei.ascend.collab.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.collab.core.WorkResult.Status;
import com.huawei.ascend.collab.sim.ScriptedWorker;
import com.huawei.ascend.collab.sim.ScriptedWorker.Behavior;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Fleet routing: a dynamic registry (membership changes at runtime), health-aware routing
 * (skip unhealthy workers), and circuit-breaker load shedding (a failing node stops getting
 * traffic, which sheds onto healthy peers).
 */
class CoordinatorScaleTest {

    private static final java.util.function.LongSupplier CLOCK = () -> 0L;

    private static Coordinator coordinator(WorkerRegistry reg, CoordinatorConfig cfg) {
        return new Coordinator(reg, ResultValidator.nonEmptyOutput(), "t", CLOCK,
                CollaborationObserver.NOOP, cfg);
    }

    @Test
    void registryMembershipIsResolvedPerDispatch() {
        InMemoryWorkerRegistry reg = new InMemoryWorkerRegistry();
        Coordinator c = coordinator(reg, CoordinatorConfig.defaults());

        // No worker yet → fails with NO_WORKER.
        CollaborationResult before = c.run(List.of(SubTask.of("t1", "cap", "p")));
        assertEquals(Status.FAILED, before.outcomes().get("t1"));
        assertTrue(before.log().stream().anyMatch(e -> e.type() == CoordinationEvent.Type.NO_WORKER));

        // Worker joins at runtime → next dispatch routes to it.
        reg.register(ScriptedWorker.of("w", "cap", Behavior.COMPLETE));
        CollaborationResult after = c.run(List.of(SubTask.of("t2", "cap", "p")));
        assertEquals(Status.COMPLETED, after.outcomes().get("t2"));
    }

    @Test
    void unhealthyWorkerIsSkippedWhenAHealthyOneExists() {
        AtomicInteger unhealthyCalls = new AtomicInteger();
        Worker unhealthy = new Worker() {
            @Override public String id() {
                return "down";
            }
            @Override public Set<String> capabilities() {
                return Set.of("cap");
            }
            @Override public boolean healthy() {
                return false;
            }
            @Override public WorkResult execute(SubTask task, TaskToken token) {
                unhealthyCalls.incrementAndGet();
                return WorkResult.failed(task.id(), token, id(), "should not be called");
            }
        };
        ScriptedWorker healthy = ScriptedWorker.of("up", "cap", Behavior.COMPLETE);
        Coordinator c = coordinator(WorkerRegistry.of(List.of(unhealthy, healthy)), CoordinatorConfig.defaults());

        CollaborationResult r = c.run(List.of(
                SubTask.of("t1", "cap", "a"),
                SubTask.of("t2", "cap", "b"),
                SubTask.of("t3", "cap", "c")));

        assertEquals(3, r.count(Status.COMPLETED), "all routed to the healthy worker");
        assertEquals(0, unhealthyCalls.get(), "the unhealthy worker never received work");
        assertEquals(3, healthy.calls());
    }

    @Test
    void circuitBreakerShedsLoadOffAFailingNode() {
        ScriptedWorker bad = ScriptedWorker.of("bad", "cap", Behavior.FAIL);
        ScriptedWorker good = ScriptedWorker.of("good", "cap", Behavior.COMPLETE);
        // Trip after a single failure; long cooldown + fixed clock keeps it open for the batch.
        CoordinatorConfig cfg = CoordinatorConfig.defaults().withCircuitBreaker(1, 60_000);
        Coordinator c = coordinator(WorkerRegistry.of(List.of(bad, good)), cfg);

        List<SubTask> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(SubTask.of("t" + i, "cap", "p" + i));
        }
        CollaborationResult r = c.run(tasks);

        assertEquals(8, r.count(Status.COMPLETED), "every task still completes via the healthy peer");
        assertTrue(bad.calls() <= 2, "the failing node is taken out of rotation fast (was " + bad.calls() + ")");
        assertTrue(good.calls() >= 7, "load shed onto the healthy node (was " + good.calls() + ")");
    }
}
