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

    /**
     * The drain must complete inside the SmartLifecycle stop phases, not at bean
     * destroy: every lifecycle stop() — including the phase-0 handler.stop() —
     * runs before destroy callbacks, so a destroy-time drain would let in-flight
     * executions call handlers whose resources are already released. The phase
     * must sit above the handler lifecycle so the drain stops first.
     */
    @Test
    void stopDrainsInFlightTaskBeforeReturning_andRunsAboveHandlerLifecyclePhase() throws Exception {
        var executor = new RuntimeAutoConfiguration.A2aServerExecutor();
        assertThat(executor.getPhase())
                .isGreaterThan(new AgentRuntimeLifecycle(java.util.List.of(), new RuntimeReadiness()).getPhase());

        executor.start();
        assertThat(executor.isRunning()).isTrue();
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean();
        executor.executor().submit(() -> {
            taskStarted.countDown();
            try {
                release.await();
                completed.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(taskStarted.await(5, TimeUnit.SECONDS)).isTrue();

        Thread stopper = new Thread(executor::stop, "a2a-test-stopper");
        stopper.start();
        stopper.join(500);
        assertThat(stopper.isAlive())
                .as("stop() must wait for the in-flight task instead of deferring the drain to destroy")
                .isTrue();

        release.countDown();
        stopper.join(5_000);
        assertThat(stopper.isAlive()).isFalse();
        assertThat(completed).isTrue();
        assertThat(executor.isRunning()).isFalse();
        assertThat(executor.executor().isTerminated())
                .as("the pool is fully drained when the stop phase completes")
                .isTrue();
    }

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
