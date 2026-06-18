package com.huawei.ascend.a2a.memory.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.a2a.memory.shared.InMemorySharedMemoryStore;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryKit;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryStore;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Economy: a deterministic model of the token lever. In a collaboration of K agents
 * where each agent needs every upstream agent's conclusion, WITHOUT shared memory a
 * downstream agent must RE-DERIVE upstream conclusions (hand-over carries payload,
 * not knowledge); WITH the shared blackboard each conclusion is derived once and
 * read by the rest. A "derivation" is a proxy for one LLM call (≈tokens), so fewer
 * derivations = fewer tokens. This quantifies the saving rather than just asserting
 * it in prose.
 */
class EconomyEvalTest {

    private static final int K = 5;            // agents per collaboration
    private static final int RUNS = 20;        // collaborations in the batch
    private static final int TOKENS_PER_DERIVATION = 100; // proxy

    @Test
    void sharedMemoryCutsRedundantDerivations() {
        int withMem = 0;
        int without = 0;
        for (int r = 0; r < RUNS; r++) {
            withMem += withSharedMemory("run-" + r);
            without += withoutSharedMemory();
        }

        // expected closed form: with = K per run; without = K(K+1)/2 per run
        assertEquals(RUNS * K, withMem, "with shared memory each conclusion derived once");
        assertEquals(RUNS * (K * (K + 1) / 2), without, "without, downstream agents re-derive upstream");
        assertTrue(withMem < without, "shared memory strictly reduces derivations");

        long tokensSaved = (long) (without - withMem) * TOKENS_PER_DERIVATION;
        double savedPct = 100.0 * (without - withMem) / without;
        System.out.printf("[economy] K=%d runs=%d  derivations with=%d without=%d  ~tokens saved=%d (%.0f%%)%n",
                K, RUNS, withMem, without, tokensSaved, savedPct);
        assertTrue(savedPct > 50.0, "material saving at K=" + K);
    }

    /** Each agent derives its own conclusion once; reads the rest from the blackboard. */
    private int withSharedMemory(String collabId) {
        SharedMemoryStore store = new InMemorySharedMemoryStore(() -> 1L);
        SharedMemoryKit board = SharedMemoryKit.forCollaboration(store, "bank", collabId);
        AtomicInteger derivations = new AtomicInteger();
        for (int i = 0; i < K; i++) {
            board.put("c" + i, derive(derivations, i), "agent-" + i); // derive own once
            for (int j = 0; j < i; j++) {
                board.get("c" + j); // read upstream — no derivation
            }
        }
        return derivations.get();
    }

    /** No shared memory: each agent re-derives every upstream conclusion it needs. */
    private int withoutSharedMemory() {
        AtomicInteger derivations = new AtomicInteger();
        for (int i = 0; i < K; i++) {
            derive(derivations, i);                 // own
            for (int j = 0; j < i; j++) {
                derive(derivations, j);             // re-derive upstream (knowledge wasn't shared)
            }
        }
        return derivations.get();
    }

    private static String derive(AtomicInteger counter, int i) {
        counter.incrementAndGet(); // proxy for an LLM call
        return "conclusion-" + i;
    }
}
