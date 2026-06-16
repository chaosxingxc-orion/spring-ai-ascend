package com.huawei.ascend.collab.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.collab.core.WorkResult.Status;
import com.huawei.ascend.collab.sim.ScriptedWorker;
import com.huawei.ascend.collab.sim.ScriptedWorker.Behavior;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CoordinatorConcurrencyTest {

    @Test
    void distributesTasksConcurrentlyAndObserves() {
        List<Worker> workers = List.of(
                ScriptedWorker.of("a", "sum", Behavior.COMPLETE),
                ScriptedWorker.of("b", "sum", Behavior.COMPLETE),
                ScriptedWorker.of("c", "sum", Behavior.COMPLETE));

        AtomicInteger completedCallbacks = new AtomicInteger();
        CollaborationObserver observer = new CollaborationObserver() {
            @Override
            public void onEvent(CoordinationEvent event) {
            }

            @Override
            public void onTaskCompleted(String taskId, Status status, long durationMs) {
                completedCallbacks.incrementAndGet();
            }
        };

        List<SubTask> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            tasks.add(SubTask.of("t" + i, "sum", "x" + i));
        }

        Coordinator coordinator = new Coordinator(workers, ResultValidator.nonEmptyOutput(),
                "test", () -> 1_000_000L, observer);
        CollaborationResult r = coordinator.runConcurrent(tasks, 4);

        assertEquals(12, r.outcomes().size());
        assertTrue(r.allCompleted(), "all 12 tasks should complete: " + r.outcomes());
        assertEquals(12, r.count(Status.COMPLETED));
        assertEquals(12, completedCallbacks.get(), "observer.onTaskCompleted fired per task");
    }
}
