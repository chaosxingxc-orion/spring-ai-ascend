package com.huawei.ascend.collab.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * dedupeResults guarantees zero-extra-token reuse for the sequential {@link Coordinator#run}:
 * two identical {@code (capability, payload)} sub-tasks dispatch the worker exactly once.
 * (Under {@code runConcurrent} dedupe is best-effort by design — see the Coordinator class
 * doc "Dedupe scope" — so this test locks the guaranteed, sequential semantics.)
 */
class CoordinatorDedupeTest {

    @Test
    void sequentialRunDedupesIdenticalTasksToOneDispatch() {
        AtomicInteger calls = new AtomicInteger();
        Worker worker = new CountingWorker("w", Set.of("cap"), calls);
        Coordinator coordinator = new Coordinator(List.of(worker),
                ResultValidator.nonEmptyOutput(), "tenant", System::currentTimeMillis,
                CollaborationObserver.NOOP, CoordinatorConfig.defaults().withDedupe(true));

        CollaborationResult r = coordinator.run(List.of(
                SubTask.of("t1", "cap", "same"),
                SubTask.of("t2", "cap", "same")));

        assertEquals(WorkResult.Status.COMPLETED, r.outcomes().get("t1"));
        assertEquals(WorkResult.Status.COMPLETED, r.outcomes().get("t2"));
        assertEquals(1, calls.get(), "identical work dispatched once; the second is deduped");
    }

    /** Counts how many times the coordinator actually dispatched work to it. */
    private static final class CountingWorker implements Worker {
        private final String id;
        private final Set<String> caps;
        private final AtomicInteger calls;

        CountingWorker(String id, Set<String> caps, AtomicInteger calls) {
            this.id = id;
            this.caps = caps;
            this.calls = calls;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Set<String> capabilities() {
            return caps;
        }

        @Override
        public WorkResult execute(SubTask task, TaskToken token) {
            calls.incrementAndGet();
            return WorkResult.completed(task.id(), "out", token, id);
        }
    }
}
