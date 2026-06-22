package com.bank.financial.research.macro;

import com.bank.financial.research.data.MacroData;
import com.bank.financial.research.engine.ReportRequest;
import com.bank.financial.research.engine.RunContext;
import com.bank.financial.research.model.ReportModel;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryStore;
import java.util.function.LongSupplier;

/**
 * Per-run context for the macro & policy pipeline: the assembled
 * {@link MacroData.Dataset} on top of the shared {@link RunContext} plumbing
 * (blackboard, model, budget, degradation).
 */
public final class MacroContext extends RunContext {

    private final MacroData.Dataset dataset;

    public MacroContext(ReportRequest request, MacroData.Dataset dataset, ReportModel model,
            SharedMemoryStore store, MemoryObserver observer, LongSupplier clock) {
        super(request, model, store, observer, clock);
        this.dataset = dataset;
    }

    public MacroData.Dataset dataset() {
        return dataset;
    }
}
