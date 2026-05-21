package com.huawei.ascend.service.runtime.orchestration.inmemory;

import com.huawei.ascend.service.runtime.posture.AppPostureGate;
import com.huawei.ascend.service.runtime.runs.Run;
import com.huawei.ascend.service.runtime.runs.spi.RunRepository;
import com.huawei.ascend.service.runtime.runs.RunStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
}
