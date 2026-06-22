package com.bank.financial.research.thematic;

import com.bank.financial.research.data.ThematicData;
import com.bank.financial.research.engine.ReportRequest;
import com.bank.financial.research.engine.RunContext;
import com.bank.financial.research.model.ReportModel;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryStore;
import java.util.function.LongSupplier;

/**
 * Per-run context for the thematic / sector-strategy pipeline. Adds the assembled
 * {@link ThematicData.Dataset} on top of the shared {@link RunContext} plumbing
 * (blackboard, model, budget, degradation) — the same blackboard the equity
 * engine uses.
 */
public final class ThematicContext extends RunContext {

    private final ThematicData.Dataset dataset;

    public ThematicContext(ReportRequest request, ThematicData.Dataset dataset, ReportModel model,
            SharedMemoryStore store, MemoryObserver observer, LongSupplier clock) {
        super(request, model, store, observer, clock);
        this.dataset = dataset;
    }

    public ThematicData.Dataset dataset() {
        return dataset;
    }
}
