package com.huawei.ascend.a2a.memory.memopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Exercises the fail-open {@link Circuit} state machine directly, including the
 * half-open single-probe transition and its behaviour under concurrent callers —
 * the failure mode that motivated the rewrite (count→open→half-open→close must be
 * observed atomically, and only one caller may probe a recovering backend).
 */
class CircuitTest {

    /** Manual clock so window expiry is deterministic, not wall-clock dependent. */
    private final AtomicLong now = new AtomicLong(1_000);

    private Circuit circuit(int threshold, long openMs) {
        return new Circuit(threshold, openMs, now::get);
    }

    @Test
    void closedAdmitsUntilThresholdConsecutiveFailures() {
        Circuit c = circuit(3, 1_000);
        assertTrue(c.tryAcquire());
        c.onFailure();
        c.onFailure();
        assertTrue(c.tryAcquire(), "still closed below threshold");
        c.onFailure(); // third consecutive failure trips it
        assertTrue(c.isOpen());
        assertFalse(c.tryAcquire(), "open window rejects calls");
    }

    @Test
    void successResetsTheConsecutiveFailureCount() {
        Circuit c = circuit(3, 1_000);
        c.onFailure();
        c.onFailure();
        c.onSuccess(); // resets — the next two failures must not trip it
        c.onFailure();
        c.onFailure();
        assertTrue(c.tryAcquire(), "counter reset by success");
    }

    @Test
    void halfOpenAdmitsExactlyOneProbeThenSuccessCloses() {
        Circuit c = circuit(1, 1_000);
        c.onFailure(); // open
        assertFalse(c.tryAcquire());
        now.addAndGet(1_000); // window elapses
        assertTrue(c.tryAcquire(), "first caller becomes the half-open probe");
        assertFalse(c.tryAcquire(), "no second probe while one is in flight");
        c.onSuccess();
        assertTrue(c.tryAcquire(), "probe success closes the circuit");
    }

    @Test
    void halfOpenProbeFailureReopensImmediately() {
        Circuit c = circuit(1, 1_000);
        c.onFailure(); // open
        now.addAndGet(1_000);
        assertTrue(c.tryAcquire()); // probe
        c.onFailure(); // probe fails → reopen, fresh window
        assertFalse(c.tryAcquire(), "reopened immediately, not after another threshold count");
        now.addAndGet(1_000);
        assertTrue(c.tryAcquire(), "next window admits a fresh probe");
    }

    @Test
    void underConcurrencyOnlyOneCallerProbesAfterWindow() throws InterruptedException {
        Circuit c = circuit(1, 1_000);
        c.onFailure(); // open
        now.addAndGet(1_000); // window elapsed: exactly one of the racers may probe

        int racers = 64;
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger admitted = new AtomicInteger();
        Thread[] threads = new Thread[racers];
        for (int i = 0; i < racers; i++) {
            threads[i] = new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (c.tryAcquire()) {
                    admitted.incrementAndGet();
                }
            });
            threads[i].start();
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        for (Thread t : threads) {
            t.join(TimeUnit.SECONDS.toMillis(5));
        }
        assertEquals(1, admitted.get(), "half-open admits exactly one probe even under a thundering herd");
    }
}
