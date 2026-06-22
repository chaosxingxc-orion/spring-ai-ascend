package com.bank.financial.research.fund;

import com.bank.financial.research.data.FundData;
import com.bank.financial.research.engine.ReportRequest;
import com.bank.financial.research.engine.RunContext;
import com.bank.financial.research.model.ReportModel;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryStore;
import java.util.function.LongSupplier;

/** Per-run context for the fund / FOF pipeline: the dataset on top of the shared {@link RunContext}. */
public final class FundContext extends RunContext {

    private final FundData.Dataset dataset;

    public FundContext(ReportRequest request, FundData.Dataset dataset, ReportModel model,
            SharedMemoryStore store, MemoryObserver observer, LongSupplier clock) {
        super(request, model, store, observer, clock);
        this.dataset = dataset;
    }

    public FundData.Dataset dataset() {
        return dataset;
    }
}
