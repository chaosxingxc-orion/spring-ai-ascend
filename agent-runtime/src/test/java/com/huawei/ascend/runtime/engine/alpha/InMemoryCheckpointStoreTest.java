package com.huawei.ascend.runtime.engine.alpha;

import com.openjiuwen.core.kernel.model.Checkpoint;
import com.openjiuwen.core.kernel.model.CheckpointId;
import com.openjiuwen.core.kernel.model.TaskId;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bearing tests for the production in-memory checkpoint store. Timestamps are injected
 * explicitly (not via {@code Checkpoint.of}, which stamps {@code Instant.now()}) so the
 * latest-by-timestamp and sort-by-timestamp assertions are deterministic — the selector
 * under test is the only variable.
 */
class InMemoryCheckpointStoreTest {

    @Test
    void saveThenLoadLatestReturnsSavedCheckpoint() {
        InMemoryCheckpointStore store = new InMemoryCheckpointStore();
        TaskId taskId = TaskId.generate();
        Checkpoint cp = Checkpoint.of(taskId, "EXECUTING", 1, "{\"progress\":50}");

        store.save(cp).block();

        StepVerifier.create(store.loadLatest(taskId))
                .assertNext(loaded -> {
                    assertThat(loaded.checkpointId()).isEqualTo(cp.checkpointId());
                    assertThat(loaded.taskId()).isEqualTo(taskId);
                    assertThat(loaded.stateJson()).isEqualTo("{\"progress\":50}");
                })
                .verifyComplete();
    }

    @Test
    void loadLatestForUnknownTaskIsEmpty() {
        InMemoryCheckpointStore store = new InMemoryCheckpointStore();
        StepVerifier.create(store.loadLatest(TaskId.generate()))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void loadLatestReturnsHighestTimestamp() {
        InMemoryCheckpointStore store = new InMemoryCheckpointStore();
        TaskId taskId = TaskId.generate();
        Instant early = Instant.parse("2026-01-01T00:00:00Z");
        Instant late = Instant.parse("2026-01-02T00:00:00Z");
        store.save(new Checkpoint(CheckpointId.generate(), taskId, "P1", 0, "{}", early)).block();
        store.save(new Checkpoint(CheckpointId.generate(), taskId, "P2", 1, "{}", late)).block();

        // IFF: strip the max(timestamp) selector (e.g. findFirst) and this returns the older one → red.
        StepVerifier.create(store.loadLatest(taskId))
                .assertNext(latest -> assertThat(latest.timestamp()).isEqualTo(late))
                .verifyComplete();
    }

    @Test
    void listReturnsAllCheckpointsSortedByTimestamp() {
        InMemoryCheckpointStore store = new InMemoryCheckpointStore();
        TaskId taskId = TaskId.generate();
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-02T00:00:00Z");
        Instant t3 = Instant.parse("2026-01-03T00:00:00Z");
        store.save(new Checkpoint(CheckpointId.generate(), taskId, "C", 2, "{}", t3)).block();
        store.save(new Checkpoint(CheckpointId.generate(), taskId, "A", 0, "{}", t1)).block();
        store.save(new Checkpoint(CheckpointId.generate(), taskId, "B", 1, "{}", t2)).block();

        // saved out of order; list must emit in timestamp order. Strip the sort and order breaks → red.
        StepVerifier.create(store.list(taskId))
                .assertNext(c -> assertThat(c.phase()).isEqualTo("A"))
                .assertNext(c -> assertThat(c.phase()).isEqualTo("B"))
                .assertNext(c -> assertThat(c.phase()).isEqualTo("C"))
                .verifyComplete();
    }

    @Test
    void listForUnknownTaskIsEmpty() {
        InMemoryCheckpointStore store = new InMemoryCheckpointStore();
        StepVerifier.create(store.list(TaskId.generate()))
                .verifyComplete();
    }
}
