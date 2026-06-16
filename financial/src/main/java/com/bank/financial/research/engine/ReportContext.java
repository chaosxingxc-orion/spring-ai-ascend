package com.bank.financial.research.engine;

import com.bank.financial.research.data.CompanyData;
import com.bank.financial.research.model.ReportModel;
import com.huawei.ascend.a2a.memory.a2a.A2aSharedMemory;
import com.huawei.ascend.a2a.memory.a2a.A2aSharedMemoryHandle;
import com.huawei.ascend.a2a.memory.auth.MemoryPrincipal;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import com.huawei.ascend.a2a.memory.shared.SharedEntry;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Per-run state shared by all sub-agents: the blackboard (canonical store),
 * the assembled dataset, the model seam, the budget, and the accumulators for
 * gaps / compliance notes / consistency findings. Each agent gets its own
 * memory handle (bound to its role as agentId), so blackboard writes carry
 * correct ownership/provenance and reads record team-interaction edges.
 */
public final class ReportContext {

    private final ReportRequest request;
    private final CompanyData.Dataset dataset;
    private final ReportModel model;
    private final SharedMemoryStore store;
    private final MemoryObserver observer;
    private final LongSupplier clock;

    private final Map<String, A2aSharedMemoryHandle> handles = new ConcurrentHashMap<>();
    private final long startMs;
    private int modelCalls = 0;

    public ReportContext(ReportRequest request, CompanyData.Dataset dataset, ReportModel model,
            SharedMemoryStore store, MemoryObserver observer, LongSupplier clock) {
        this.request = request;
        this.dataset = dataset;
        this.model = model;
        this.store = store;
        this.observer = observer;
        this.clock = clock;
        this.startMs = clock.getAsLong();
    }

    public ReportRequest request() {
        return request;
    }

    public CompanyData.Dataset dataset() {
        return dataset;
    }

    public ReportModel model() {
        return model;
    }

    public long now() {
        return clock.getAsLong();
    }

    /** This agent's blackboard handle (cached); writes are owned by {@code role}. */
    public A2aSharedMemoryHandle memory(String role) {
        return handles.computeIfAbsent(role, r ->
                A2aSharedMemory.forCollaboration(
                        MemoryPrincipal.agent(request.tenantId(), r), request.collaborationId(), store, observer));
    }

    // ── Convenience writes (attributed to the calling role) ───────────────────

    public void put(String role, String key, String value) {
        memory(role).put(key, value);
    }

    public void putNum(String role, String key, double value) {
        memory(role).put(key, Bb.fmt(value));
    }

    // ── Engine-internal reads (no READ edge; used by orchestrator + checker) ───

    public Optional<String> latest(String key) {
        return store.latest(request.tenantId(), request.collaborationId(), key).map(SharedEntry::value);
    }

    public OptionalDouble latestNum(String key) {
        Optional<String> v = latest(key);
        if (v.isEmpty()) {
            return OptionalDouble.empty();
        }
        try {
            return OptionalDouble.of(Double.parseDouble(v.get()));
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    public List<String> blackboardKeys() {
        return store.keys(request.tenantId(), request.collaborationId());
    }

    // ── Budget ────────────────────────────────────────────────────────────────

    public int modelCalls() {
        return modelCalls;
    }

    /** Try to spend one model call against the budget; false when exhausted. */
    public boolean tryModelCall() {
        if (modelCalls >= request.budget().maxModelCalls()) {
            return false;
        }
        modelCalls++;
        return true;
    }

    /** True while within the wall-clock budget (0 = unlimited). */
    public boolean withinTime() {
        long limit = request.budget().timeoutMs();
        return limit <= 0 || (now() - startMs) < limit;
    }
}
