package com.huawei.ascend.service.runtime.orchestration.inmemory;

import com.huawei.ascend.service.runtime.posture.AppPostureGate;
import com.huawei.ascend.service.runtime.runs.Run;
import com.huawei.ascend.service.runtime.runs.RunStateMachine;
import com.huawei.ascend.service.runtime.runs.spi.RunRepository;
import com.huawei.ascend.service.runtime.runs.RunStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Dev-posture RunRepository backed by a ConcurrentHashMap.
 * Used by the reference in-memory executor path (no Postgres required at W0/test).
 */
public final class InMemoryRunRegistry implements RunRepository {

    private final Map<UUID, Run> store = new ConcurrentHashMap<>();

    public InMemoryRunRegistry() {
        AppPostureGate.requireDevForInMemoryComponent("InMemoryRunRegistry");
    }

    @Override
    public Optional<Run> findById(UUID runId) {
        return Optional.ofNullable(store.get(runId));
    }

    @Override
    public Run save(Run run) {
        store.put(run.runId(), run);
        return run;
    }

    @Override
    public List<Run> findByTenant(String tenantId) {
        return store.values().stream()
                .filter(r -> tenantId.equals(r.tenantId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Run> findByParentRunId(UUID parentRunId) {
        return store.values().stream()
                .filter(r -> parentRunId.equals(r.parentRunId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Run> findByTenantAndStatus(String tenantId, RunStatus status) {
        return store.values().stream()
                .filter(r -> tenantId.equals(r.tenantId()) && status == r.status())
                .collect(Collectors.toList());
    }

    @Override
    public List<Run> findRootRuns(String tenantId) {
        return store.values().stream()
                .filter(r -> tenantId.equals(r.tenantId()) && r.parentRunId() == null)
                .collect(Collectors.toList());
    }

    /**
     * Atomic compare-and-set: the re-read, tenant check, terminal check, and write
     * happen inside a single {@link ConcurrentHashMap#computeIfPresent} remapping
     * (per-key locked), so a parallel terminal write can never be silently overwritten
     * by a stale-snapshot caller and a cross-tenant write can never reach the row.
     * The mutator is invoked on the freshly-read Run, so {@code Run.withStatus}'s
     * state-machine validation also sees current state.
     *
     * <p>Tenant-mismatch returns empty (cross-tenant access collapses to "not found"
     * per Rule R-J.b W0 posture) — the row is left untouched.
     */
    @Override
    public Optional<Run> updateIfNotTerminal(String tenantId, UUID runId, UnaryOperator<Run> mutator) {
        java.util.Objects.requireNonNull(tenantId, "tenantId is required");
        java.util.Objects.requireNonNull(runId, "runId is required");
        // Track tenant-mismatch separately so we can return Optional.empty() (cross-tenant
        // collapse) rather than the unchanged foreign Run.
        boolean[] tenantMismatch = {false};
        Run updated = store.computeIfPresent(runId, (k, current) -> {
            if (!tenantId.equals(current.tenantId())) {
                tenantMismatch[0] = true;
                return current; // leave foreign row untouched
            }
            if (RunStateMachine.isTerminal(current.status())) {
                return current;
            }
            try {
                return mutator.apply(current);
            } catch (IllegalStateException illegalTransition) {
                // Non-terminal but the mutator's target is unreachable from the current
                // status (e.g. FAILED -> CANCELLED: FAILED is non-terminal — it permits
                // ->RUNNING retry — but not ->CANCELLED). Leave the Run unchanged so the
                // caller observes the current status and maps it to 409, not a 500.
                return current;
            }
        });
        if (tenantMismatch[0]) {
            return Optional.empty();
        }
        return Optional.ofNullable(updated);
    }
}
