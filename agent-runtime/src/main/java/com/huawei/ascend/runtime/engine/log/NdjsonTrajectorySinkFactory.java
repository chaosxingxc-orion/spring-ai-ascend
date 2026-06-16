package com.huawei.ascend.runtime.engine.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.spi.TrajectorySink;
import com.huawei.ascend.runtime.engine.spi.TrajectorySinkFactory;

/**
 * Builds a fresh per-invocation {@link NdjsonTrajectorySink} over a shared {@link ObjectMapper}.
 * Registered by the log-conditional boot configuration; the resulting factory joins the
 * per-invocation sink fan-out. Needs no optional classpath dependency (Jackson is always
 * present), unlike the OTel rail.
 */
public final class NdjsonTrajectorySinkFactory implements TrajectorySinkFactory {

    private final ObjectMapper mapper;

    public NdjsonTrajectorySinkFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public TrajectorySink create() {
        return new NdjsonTrajectorySink(mapper);
    }
}
