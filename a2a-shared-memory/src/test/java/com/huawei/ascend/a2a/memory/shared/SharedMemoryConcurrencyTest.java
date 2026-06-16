package com.huawei.ascend.a2a.memory.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Append and ownership stay atomic under concurrent writers (runtime is heavily concurrent). */
class SharedMemoryConcurrencyTest {

    @Test
    void concurrentOwnerAppendsGetDistinctSequentialVersions() throws Exception {
        SharedMemoryStore store = new InMemorySharedMemoryStore(() -> 7L);
        int n = 64;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        try {
            for (int i = 0; i < n; i++) {
                int v = i;
                pool.submit(() -> {
                    await(start);
                    store.append("t", "c", "k", "v" + v, "owner");
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "all appends finished");
        } finally {
            pool.shutdownNow();
        }
        List<SharedEntry> history = store.history("t", "c", "k");
        assertEquals(n, history.size(), "every append landed");
        // versions are exactly 1..n with no duplicates → version assignment was atomic
        assertEquals(IntStream.rangeClosed(1, n).boxed().toList(),
                history.stream().map(SharedEntry::version).sorted().toList());
    }

    @Test
    void concurrentNonOwnerWritesAreAllRejected() throws Exception {
        SharedMemoryStore store = new InMemorySharedMemoryStore(() -> 7L);
        store.append("t", "c", "k", "owned", "owner"); // owner claims the key
        int n = 32;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        try {
            for (int i = 0; i < n; i++) {
                pool.submit(() -> {
                    await(start);
                    try {
                        store.append("t", "c", "k", "intruder", "other-agent");
                    } catch (OwnershipViolationException expected) {
                        rejected.incrementAndGet();
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(n, rejected.get(), "every non-owner write rejected under concurrency");
        assertEquals(1, store.history("t", "c", "k").size(), "no intruder write landed");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
