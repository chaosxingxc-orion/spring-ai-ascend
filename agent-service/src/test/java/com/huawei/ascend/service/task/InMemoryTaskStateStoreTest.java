package com.huawei.ascend.service.task;

import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryTaskStateStoreTest {

    @Test
    void saveRejectsSequentialCrossTenantOverwrite() {
        InMemoryTaskStateStore store = new InMemoryTaskStateStore();
        String taskId = UUID.randomUUID().toString();

        store.save(taskId, "tenant-a", Map.of("step_number", 1));

        assertThatThrownBy(() -> store.save(taskId, "tenant-b", Map.of("step_number", 2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cross-tenant overwrite forbidden");
        assertThat(store.load(taskId, "tenant-a")).contains(Map.of("step_number", 1));
        assertThat(store.load(taskId, "tenant-b")).isEmpty();
    }

    @Test
    void concurrentCrossTenantFirstWritesAllowExactlyOneWinner() throws Exception {
        InMemoryTaskStateStore store = new InMemoryTaskStateStore();
        String taskId = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch bothCopiesReached = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(saveAttempt(store, taskId, "tenant-a", start, bothCopiesReached));
            Future<Boolean> second = executor.submit(saveAttempt(store, taskId, "tenant-b", start, bothCopiesReached));

            start.countDown();

            long winners = Stream.of(first, second)
                    .map(InMemoryTaskStateStoreTest::join)
                    .filter(Boolean::booleanValue)
                    .count();

            assertThat(winners).isEqualTo(1);
            assertThat(store.load(taskId, "tenant-a").isPresent()
                    ^ store.load(taskId, "tenant-b").isPresent()).isTrue();
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    private static Callable<Boolean> saveAttempt(
            InMemoryTaskStateStore store,
            String taskId,
            String tenantId,
            CountDownLatch start,
            CountDownLatch bothCopiesReached) {
        return () -> {
            start.await(1, TimeUnit.SECONDS);
            try {
                store.save(taskId, tenantId, new CopyDelayMap(tenantId, bothCopiesReached));
                return true;
            } catch (IllegalStateException expectedCrossTenantOverwrite) {
                return false;
            }
        };
    }

    private static Boolean join(Future<Boolean> future) {
        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static final class CopyDelayMap extends AbstractMap<String, Object> {
        private final String marker;
        private final CountDownLatch bothCopiesReached;

        private CopyDelayMap(String marker, CountDownLatch bothCopiesReached) {
            this.marker = marker;
            this.bothCopiesReached = bothCopiesReached;
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            bothCopiesReached.countDown();
            try {
                bothCopiesReached.await(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Set.of(Map.entry("tenant_marker", marker));
        }
    }
}
