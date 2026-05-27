package com.huawei.ascend.service.task;

import com.huawei.ascend.service.task.spi.TaskStateStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Reference in-memory implementation of {@link TaskStateStore} per
 * ADR-0100.
 *
 * <p>Posture-gated for dev/research; production JDBC impl
 * ({@code JdbcTaskStateStore}) + Flyway migration with RLS per
 * Rule R-J.a land.
 *
 * <p>Tenant scope is enforced: {@link #load(String, String)} returns
 * empty when the caller's tenantId does not match the stored
 * tenantId (Rule R-C.c tenant propagation purity).
 */
public class InMemoryTaskStateStore implements TaskStateStore {

    private record TaskEntry(String tenantId, long revision, Map<String, Object> state) {
    }

    private final Map<String, TaskEntry> store = new ConcurrentHashMap<>();

    @Override
    public void save(String taskId, String tenantId, Map<String, Object> state) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(state, "state");
        store.compute(taskId, (id, existing) -> {
            if (existing != null && !existing.tenantId().equals(tenantId)) {
                throw new IllegalStateException(
                    "cross-tenant overwrite forbidden: taskId=" + taskId
                    + " existing tenant=" + existing.tenantId()
                    + " caller tenant=" + tenantId);
            }
            long nextRevision = existing == null ? 1L : existing.revision() + 1L;
            return new TaskEntry(tenantId, nextRevision, new HashMap<>(state));
        });
    }

    @Override
    public Optional<Map<String, Object>> load(String taskId, String tenantId) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(tenantId, "tenantId");
        TaskEntry entry = store.get(taskId);
        if (entry == null) {
            return Optional.empty();
        }
        // Tenant scope check (Rule R-C.c): cross-tenant access returns empty.
        if (!entry.tenantId().equals(tenantId)) {
            return Optional.empty();
        }
        return Optional.of(Map.copyOf(entry.state()));
    }

    /**
     * Atomic compare-and-update: the tenant check, revision check, mutation apply,
     * and write happen inside a single {@link ConcurrentHashMap#computeIfPresent}
     * remapping (per-key locked), so a parallel writer cannot land between the
     * check and the write.
     *
     * <p>Returns Optional.of(newRevision) on success. Returns Optional.empty()
     * on any of: tenant mismatch, task not found, revision mismatch.
     */
    @Override
    public Optional<Long> compareAndUpdate(String tenantId, String taskId, long expectedRevision,
                                           UnaryOperator<Map<String, Object>> mutation) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(mutation, "mutation");
        long[] newRevision = {-1L};
        store.computeIfPresent(taskId, (id, existing) -> {
            if (!existing.tenantId().equals(tenantId)) {
                return existing; // cross-tenant: leave untouched
            }
            if (existing.revision() != expectedRevision) {
                return existing; // revision mismatch: leave untouched
            }
            Map<String, Object> mutated = mutation.apply(Map.copyOf(existing.state()));
            Objects.requireNonNull(mutated, "mutation must not return null");
            newRevision[0] = existing.revision() + 1L;
            return new TaskEntry(tenantId, newRevision[0], new HashMap<>(mutated));
        });
        return newRevision[0] < 0 ? Optional.empty() : Optional.of(newRevision[0]);
    }

    @Override
    public Optional<Long> revisionOf(String tenantId, String taskId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(taskId, "taskId");
        TaskEntry entry = store.get(taskId);
        if (entry == null || !entry.tenantId().equals(tenantId)) {
            return Optional.empty();
        }
        return Optional.of(entry.revision());
    }
}
