package com.bank.financial.research.bond;

import com.bank.financial.research.data.BondData;
import com.bank.financial.research.engine.ReportRequest;
import com.bank.financial.research.engine.RunContext;
import com.bank.financial.research.model.ReportModel;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryStore;
import java.util.function.LongSupplier;

/** Per-run context for the bond / fixed-income pipeline: the dataset on top of the shared {@link RunContext}. */
public final class BondContext extends RunContext {

    private final BondData.Dataset dataset;

    public BondContext(ReportRequest request, BondData.Dataset dataset, ReportModel model,
            SharedMemoryStore store, MemoryObserver observer, LongSupplier clock) {
        super(request, model, store, observer, clock);
        this.dataset = dataset;
    }

    public BondData.Dataset dataset() {
        return dataset;
    }
}
