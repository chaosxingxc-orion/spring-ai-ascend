package com.huawei.ascend.service.task;

import com.huawei.ascend.service.task.spi.TaskStateStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reference in-memory implementation of {@link TaskStateStore} per
 * ADR-0100 (rc24).
 *
 * <p>Posture-gated for dev/research; production JDBC impl
 * ({@code JdbcTaskStateStore}) + Flyway migration with RLS per
 * Rule R-J.a land in rc25.
 *
 * <p>Tenant scope is enforced: {@link #load(String, String)} returns
 * empty when the caller's tenantId does not match the stored
 * tenantId (Rule R-C.c tenant propagation purity).
 */
public class InMemoryTaskStateStore implements TaskStateStore {

    private record TaskEntry(String tenantId, Map<String, Object> state) {
    }

    private final Map<String, TaskEntry> store = new ConcurrentHashMap<>();

    @Override
    public void save(String taskId, String tenantId, Map<String, Object> state) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(state, "state");
        store.put(taskId, new TaskEntry(tenantId, new HashMap<>(state)));
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
}
