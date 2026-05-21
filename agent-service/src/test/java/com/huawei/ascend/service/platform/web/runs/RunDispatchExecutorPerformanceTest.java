package com.huawei.ascend.service.platform.web.runs;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance-safety proof for run dispatch executor isolation.
 *
 * <p>Scenario: saturate ForkJoin common pool workers with blocking tasks, then
 * submit one probe task to common pool and one probe task to the dedicated run
 * dispatch executor. The dedicated executor should run promptly while the common
 * pool probe waits for blocked workers to free up.
 */
class RunDispatchExecutorPerformanceTest {

    @Test
    void dedicatedExecutorStartsProbeEarlierThanSaturatedCommonPool() throws Exception {
        int commonParallelism = ForkJoinPool.getCommonPoolParallelism();
        assertThat(commonParallelism)
                .as("common pool must expose at least one worker")
                .isGreaterThan(0);

        CountDownLatch blockersStarted = new CountDownLatch(commonParallelism);
        CountDownLatch releaseBlockers = new CountDownLatch(1);
        List<CompletableFuture<Void>> blockers = new ArrayList<>();

        for (int i = 0; i < commonParallelism; i++) {
            blockers.add(CompletableFuture.runAsync(() -> {
                blockersStarted.countDown();
                try {
                    releaseBlockers.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        assertThat(blockersStarted.await(1, TimeUnit.SECONDS))
                .as("all common-pool workers should be occupied by blockers")
                .isTrue();

        Executor dedicated = new RunDispatchExecutorConfiguration(
                new SimpleMeterRegistry(),
                new RunDispatchProperties()).runDispatchExecutor();

        long commonStart = System.nanoTime();
        CompletableFuture<Long> commonProbe = CompletableFuture.supplyAsync(System::nanoTime);

        long dedicatedStart = System.nanoTime();
        CompletableFuture<Long> dedicatedProbe = CompletableFuture.supplyAsync(System::nanoTime, dedicated);

        long dedicatedNanos = dedicatedProbe.get(500, TimeUnit.MILLISECONDS) - dedicatedStart;

        // common probe should not finish while blockers still occupy all workers.
        assertThat(commonProbe.isDone()).isFalse();

        releaseBlockers.countDown();
        CompletableFuture.allOf(blockers.toArray(new CompletableFuture[0])).join();
        long commonNanos = commonProbe.get(2, TimeUnit.SECONDS) - commonStart;

        assertThat(Duration.ofNanos(dedicatedNanos))
                .as("dedicated run-dispatch executor should schedule promptly")
                .isLessThan(Duration.ofMillis(200));
        assertThat(commonNanos)
                .as("common pool probe should be delayed while pool is saturated")
                .isGreaterThan(dedicatedNanos);

        if (dedicated instanceof ThreadPoolExecutor tpe) {
            tpe.shutdownNow();
        }
    }
}
