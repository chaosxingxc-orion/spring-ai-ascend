package com.huawei.ascend.a2a.memory.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.a2a.memory.shared.InMemorySharedMemoryStore;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryKit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Elasticity: thousands of concurrent A2A collaborations over a single
 * collaboration-partitioned store stay correct and isolated — evidence the design
 * scales horizontally (one shared store, partitioned by collaboration, not a
 * structure per collaboration).
 */
class ScaleTest {

    @Test
    void thousandsOfConcurrentCollaborationsStayIsolatedAndCorrect() throws Exception {
        InMemorySharedMemoryStore store = new InMemorySharedMemoryStore(() -> 1L);
        int collaborations = 2_000;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        AtomicInteger errors = new AtomicInteger();
        try {
            for (int i = 0; i < collaborations; i++) {
                String collabId = "collab-" + i;
                pool.submit(() -> {
                    try {
                        SharedMemoryKit board = SharedMemoryKit.forCollaboration(store, "bank", collabId);
                        board.put("risk", "C3-" + collabId, "risk-agent");
                        board.put("loan", "ok-" + collabId, "loan-agent");
                        // each collaboration only sees its own blackboard
                        if (!("C3-" + collabId).equals(board.get("risk").orElse(""))) {
                            errors.incrementAndGet();
                        }
                        if (board.keys().size() != 2) {
                            errors.incrementAndGet();
                        }
                    } catch (RuntimeException e) {
                        errors.incrementAndGet();
                    }
                });
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "all collaborations finished");
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, errors.get(), "no cross-collaboration leakage or contention error at scale");
    }
}
