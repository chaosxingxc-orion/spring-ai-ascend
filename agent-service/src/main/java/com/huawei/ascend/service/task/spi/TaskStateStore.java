package com.huawei.ascend.service.task.spi;

import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Task control-state persistence SPI.
 *
 * <p>The Task Center component (5-component decomposition
 * of agent-service) is responsible for TaskControlState persistence.
 * {@code Task} is the control-state layer in the Run ≤ Task ≤ Session ≤
 * Memory lifecycle hierarchy.
 *
 * <p>TaskID and SessionID are logically decoupled: one Session may
 * concurrently execute multiple Tasks; one Task may drift across
 * multiple Sessions. See ADR-0100 §decision.
 *
 * <p>Reference impl ({@code InMemoryTaskStateStore}) lands;
 * JDBC impl ({@code JdbcTaskStateStore}) + Flyway migration land in
 * rc25 with RLS per Rule R-J.a.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} + own
 * siblings.
 */
public interface TaskStateStore {

    /**
     * Persist or update a task's control state. Each call bumps the task's revision
     * by one; the initial revision after a first save is 1.
     *
     * <p>For modify paths that need to detect concurrent updates, use
     * {@link #compareAndUpdate(String, String, long, UnaryOperator)} instead —
     * {@code save(...)} is a blind put.
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

    /**
     * Conditionally update a task's state iff the persisted revision matches
     * {@code expectedRevision} AND the persisted tenantId matches {@code tenantId}.
     * Atomic per (tenantId, taskId). Returns the new revision on success, empty on
     * any of: tenant mismatch, task not found, revision mismatch.
     *
     * <p>Closes IF-DRIFT-002 from PR #76's interface-drift review: callers
     * previously needed to do {@code load -> mutate -> save} which has a
     * read-modify-write race window. The CAS primitive makes the re-read +
     * revision check + write a single atomic step, mirroring the
     * {@code RunRepository.updateIfNotTerminal} CAS pattern (Rule R-C.2.b +
     * ADR-0118 + ADR-0142).
     *
     * <p>Revision semantics: each successful {@link #save(String, String, Map)}
     * and {@code compareAndUpdate(...)} increments the revision by one. Callers
     * that don't already track revisions can read the current revision via
     * {@link #revisionOf(String, String)} before invoking this method.
     *
     * @param tenantId         mandatory; cross-tenant attempt returns empty.
     * @param taskId           the Task ID.
     * @param expectedRevision the revision the caller expects to see; mismatch returns empty.
     * @param mutation         applied to a defensive copy of the current state; the returned
     *                         map replaces the persisted state. The mutation MUST be pure
     *                         (no side effects) because it may be invoked on a stale snapshot
     *                         in the impl's retry loop, if any.
     * @return Optional.of(newRevision) on successful apply; Optional.empty() on rejection.
     */
    Optional<Long> compareAndUpdate(String tenantId, String taskId, long expectedRevision,
                                    UnaryOperator<Map<String, Object>> mutation);

    /**
     * Returns the current revision of a task within tenant scope, or empty if
     * not found / cross-tenant. Counterpart to
     * {@link #compareAndUpdate(String, String, long, UnaryOperator)}.
     *
     * @param tenantId mandatory per Rule R-C.c.
     * @param taskId   the Task ID.
     * @return the current revision, or empty if not found / cross-tenant.
     */
    Optional<Long> revisionOf(String tenantId, String taskId);
}
