package com.huawei.ascend.a2a.memory.perf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.a2a.memory.shared.InMemorySharedMemoryStore;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryKit;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryStore;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Runtime hot-path micro-benchmark for the in-process blackboard (put/get): reports
 * throughput + p50/p99 latency and asserts a generous, non-flaky floor. The closed
 * engine's persistence/recall performance is separate; this guards that the kit
 * layer itself is not a bottleneck on the runtime path.
 */
class PerfBenchmarkTest {

    private static final int WARMUP = 20_000;
    private static final int N = 100_000;

    @Test
    void blackboardHotPathThroughputAndLatency() {
        SharedMemoryStore store = new InMemorySharedMemoryStore(() -> 1L);
        SharedMemoryKit board = SharedMemoryKit.forCollaboration(store, "bank", "perf");

        for (int i = 0; i < WARMUP; i++) {
            board.put("k" + (i % 512), "v", "agent");
            board.get("k" + (i % 512));
        }

        long[] putNs = new long[N];
        long[] getNs = new long[N];
        long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            String key = "k" + (i % 512); // 512 keys → bounded append-logs, realistic working set
            long t0 = System.nanoTime();
            board.put(key, "v" + i, "agent");
            putNs[i] = System.nanoTime() - t0;

            long t1 = System.nanoTime();
            board.get(key);
            getNs[i] = System.nanoTime() - t1;
        }
        long totalMs = (System.nanoTime() - start) / 1_000_000L;
        long opsPerSec = totalMs == 0 ? Long.MAX_VALUE : (2L * N * 1000L) / totalMs;

        Arrays.sort(putNs);
        Arrays.sort(getNs);
        System.out.printf("[perf] %d put+get ops in %d ms = %d ops/s | put p50=%dµs p99=%dµs | get p50=%dµs p99=%dµs%n",
                2 * N, totalMs, opsPerSec,
                putNs[N / 2] / 1000, putNs[(int) (N * 0.99)] / 1000,
                getNs[N / 2] / 1000, getNs[(int) (N * 0.99)] / 1000);

        // generous, non-flaky floors (in-process should be far above these)
        assertTrue(opsPerSec > 50_000, "hot-path throughput floor: got " + opsPerSec + " ops/s");
        assertTrue(getNs[N / 2] < 1_000_000, "get p50 under 1ms");
    }
}
