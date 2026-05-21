package com.huawei.ascend.service.task.spi;

import java.util.Map;
import java.util.Optional;

/**
 * Task control-state persistence SPI per ADR-0100 (rc22).
 *
 * <p>The Task Center component (per ADR-0100 5-component decomposition
 * of agent-service) is responsible for TaskControlState persistence.
 * {@code Task} is the control-state layer in the Run ≤ Task ≤ Session ≤
 * Memory lifecycle hierarchy.
 *
 * <p>TaskID and SessionID are logically decoupled: one Session may
 * concurrently execute multiple Tasks; one Task may drift across
 * multiple Sessions. See ADR-0100 §decision.
 *
 * <p>Reference impl ({@code InMemoryTaskStateStore}) lands in rc24;
 * JDBC impl ({@code JdbcTaskStateStore}) + Flyway migration land in
 * rc25 with RLS per Rule R-J.a.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} + own
 * siblings.
 */
public interface TaskStateStore {

    /**
     * Persist or update a task's control state.
     *
     * @param taskId   the Task ID.
     * @param tenantId mandatory per Rule R-C.c.
     * @param state    control-state map (step_number, why_stopped, task_kind, a2a_state, ...).
     */
    void save(String taskId, String tenantId, Map<String, Object> state);

    /**
     * Load a task's current control state.
     *
     * @param taskId   the Task ID.
     * @param tenantId mandatory per Rule R-C.c (must match owning tenant; return empty on mismatch).
     * @return the control-state map, or empty if not found / cross-tenant.
     */
    Optional<Map<String, Object>> load(String taskId, String tenantId);
}
