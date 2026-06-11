package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Closing the A2A serving pool is part of the runtime drain: an in-flight
 * execution must be allowed to finish (stop dispatching happened upstream),
 * not be interrupted mid-task — interruption would tear a running agent
 * execution and surface as a spurious failure during shutdown.
 */
class A2aServerExecutorTest {

    @Test
    void closeDrainsInFlightTaskInsteadOfInterruptingIt() throws Exception {
        var executor = new RuntimeAutoConfiguration.A2aServerExecutor();
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicBoolean completed = new AtomicBoolean();

        executor.executor().submit(() -> {
            taskStarted.countDown();
            try {
                release.await();
                completed.set(true);
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });
        assertThat(taskStarted.await(5, TimeUnit.SECONDS)).isTrue();

        Thread closer = new Thread(executor::close, "a2a-test-closer");
        closer.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!executor.executor().isShutdown() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(executor.executor().isShutdown()).isTrue();
        release.countDown();
        closer.join(5_000);

        assertThat(closer.isAlive()).isFalse();
        assertThat(completed).isTrue();
        assertThat(interrupted).isFalse();
    }
}
