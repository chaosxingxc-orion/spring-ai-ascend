package com.huawei.ascend.service.runtime.runs.spi;

import com.huawei.ascend.service.runtime.runs.Run;
import com.huawei.ascend.service.runtime.runs.RunStateMachine;
import com.huawei.ascend.service.runtime.runs.RunStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * SPI for run persistence. W0 dev: InMemoryRunRegistry. W2: Spring Data JDBC CrudRepository
 * backed by Postgres (per multi_backend_checkpointer; ADR-0021 layered SPI taxonomy).
 * Pure-Java types only (no Spring imports) per ARCHITECTURE.md §4 constraint 7.
 *
 * <p>SPI-pure per CLAUDE.md Rule 32: imports {@code java.*} + sibling domain
 * value types ({@code Run}, {@code RunStatus}) which form the lifecycle
 * vocabulary this SPI persists.
 */
public interface RunRepository {
    Optional<Run> findById(UUID runId);
    Run save(Run run);
    List<Run> findByTenant(String tenantId);
    List<Run> findByParentRunId(UUID parentRunId);
    List<Run> findByTenantAndStatus(String tenantId, RunStatus status);
    /** Returns top-level runs for a tenant — runs with no parent (parentRunId == null). */
    List<Run> findRootRuns(String tenantId);

    /**
     * Atomically apply {@code mutator} to the persisted Run iff its current status is
     * non-terminal, then persist and return the result. If the persisted status is
     * terminal, the mutator is NOT applied and the unchanged terminal Run is returned.
     * Returns empty when no Run exists for {@code runId}.
     *
     * <p>Closes the read-modify-write race on status transitions: a stale snapshot
     * caller (e.g. {@code RunController.cancel} racing the orchestrator's terminal
     * write) could otherwise validate a transition against the stale status and
     * blind-overwrite a parallel terminal state. The re-read, terminal check, and
     * write MUST be a single atomic step. The W2 Postgres impl satisfies this with a
     * conditional UPDATE (compare-and-set); this default is a correct non-atomic
     * fallback for impls that do not override it.
     */
    default Optional<Run> updateIfNotTerminal(UUID runId, UnaryOperator<Run> mutator) {
        Optional<Run> found = findById(runId);
        if (found.isEmpty() || RunStateMachine.isTerminal(found.get().status())) {
            return found;
        }
        try {
            return Optional.of(save(mutator.apply(found.get())));
        } catch (IllegalStateException illegalTransition) {
            // Non-terminal but the mutator's target is unreachable from the current
            // status (e.g. FAILED -> CANCELLED). Return the unchanged Run so the caller
            // maps it to 409 illegal_state_transition rather than surfacing a 500.
            return found;
        }
    }
}
