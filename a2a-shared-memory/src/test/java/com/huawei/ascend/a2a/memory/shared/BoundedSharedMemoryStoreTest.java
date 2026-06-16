package com.huawei.ascend.a2a.memory.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Backpressure: at capacity, an op is shed (rejected) instead of piling onto the backend. */
class BoundedSharedMemoryStoreTest {

    @Test
    void shedsLoadAndReportsWhenAtCapacity() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        BlockingStore blocking = new BlockingStore(entered, release);
        Recording obs = new Recording();
        // capacity 1, short acquire timeout
        BoundedSharedMemoryStore bounded = new BoundedSharedMemoryStore(blocking, 1, 50, obs);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // hold the only permit by entering a blocking append on another thread
            pool.submit(() -> bounded.append("bank", "c1", "k", "v", "a", null));
            assertTrue(entered.await(2, TimeUnit.SECONDS), "first op holds the permit");

            // a second op can't acquire within the timeout → shed
            assertThrows(BackpressureRejectedException.class, () -> bounded.latest("bank", "c1", "k"));
            assertEquals(1, bounded.rejectedCount(), "the shed op is counted");
            assertTrue(obs.degraded.contains("shared.latest:backpressure-rejected"), "saturation observed");
        } finally {
            release.countDown();
            pool.shutdown();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void passesThroughUnderCapacity() {
        BoundedSharedMemoryStore bounded =
                new BoundedSharedMemoryStore(new InMemorySharedMemoryStore(() -> 1L), 4, 1_000);
        bounded.append("bank", "c1", "k", "v", "a", null);
        assertEquals("v", bounded.latest("bank", "c1", "k").orElseThrow().value(), "normal ops unaffected");
        assertEquals(0, bounded.rejectedCount());
    }

    /** A store whose append parks (holding the permit) until released. */
    private static final class BlockingStore implements SharedMemoryStore {
        private final CountDownLatch entered;
        private final CountDownLatch release;

        BlockingStore(CountDownLatch entered, CountDownLatch release) {
            this.entered = entered;
            this.release = release;
        }

        @Override
        public SharedEntry append(String t, String c, String k, String v, String w, String idem) {
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new SharedEntry(k, v, w, 1, 1L);
        }

        @Override public Optional<SharedEntry> latest(String t, String c, String k) {
            return Optional.empty();
        }
        @Override public List<SharedEntry> history(String t, String c, String k) {
            return List.of();
        }
        @Override public List<String> keys(String t, String c) {
            return List.of();
        }
        @Override public void release(String t, String c) {
        }
    }

    private static final class Recording implements MemoryObserver {
        final List<String> degraded = new java.util.concurrent.CopyOnWriteArrayList<>();
        final AtomicInteger ops = new AtomicInteger();

        @Override public void onOperation(String op, String scope, boolean ok, long latencyMs) {
            ops.incrementAndGet();
        }
        @Override public void onDegraded(String op, String scope, String reason) {
            degraded.add(op + ":" + reason);
        }
    }
}
