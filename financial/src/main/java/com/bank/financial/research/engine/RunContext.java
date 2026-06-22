package com.bank.financial.research.engine;

import com.bank.financial.research.model.ReportModel;
import com.huawei.ascend.a2a.memory.a2a.A2aSharedMemory;
import com.huawei.ascend.a2a.memory.a2a.A2aSharedMemoryHandle;
import com.huawei.ascend.a2a.memory.auth.MemoryPrincipal;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import com.huawei.ascend.a2a.memory.shared.SharedEntry;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryStore;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dataset-agnostic per-run plumbing shared by every report pipeline (equity and
 * thematic): the shared-memory blackboard, the model seam, the budget, and the
 * graceful-degradation accumulator. Concrete contexts ({@link ReportContext},
 * {@code ThematicContext}) extend this and add their typed dataset, so the two
 * pipelines reuse one blackboard/budget/observability implementation.
 */
public class RunContext {

    private static final Logger log = LoggerFactory.getLogger("research.engine");

    private final ReportRequest request;
    private final ReportModel model;
    private final SharedMemoryStore store;
    private final MemoryObserver observer;
    private final LongSupplier clock;

    private final java.util.Map<String, A2aSharedMemoryHandle> handles = new ConcurrentHashMap<>();
    private final List<String> degradations = Collections.synchronizedList(new java.util.ArrayList<>());
    private final long startMs;
    private int modelCalls = 0;

    protected RunContext(ReportRequest request, ReportModel model, SharedMemoryStore store,
            MemoryObserver observer, LongSupplier clock) {
        this.request = request;
        this.model = model;
        this.store = store;
        this.observer = observer;
        this.clock = clock;
        this.startMs = clock.getAsLong();
    }

    public ReportRequest request() {
        return request;
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

    public void put(String role, String key, String value) {
        memory(role).put(key, value);
    }

    public void putNum(String role, String key, double value) {
        memory(role).put(key, Bb.fmt(value));
    }

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

    /** Record that {@code where} degraded for {@code reason}; surfaced in metadata + WARN log. */
    public void degraded(String where, String reason) {
        String note = where + ": " + (reason == null ? "unknown" : reason);
        degradations.add(note);
        log.warn("research-report degraded run={} {}", request.collaborationId(), note);
    }

    /** Degradations recorded during the run (empty = no fallback path was taken). */
    public List<String> degradations() {
        return List.copyOf(degradations);
    }
}
