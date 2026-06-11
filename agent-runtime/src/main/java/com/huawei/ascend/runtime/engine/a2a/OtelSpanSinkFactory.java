package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.spi.TrajectorySink;
import com.huawei.ascend.runtime.engine.spi.TrajectorySinkFactory;
import io.opentelemetry.api.trace.Tracer;

/**
 * Builds a fresh per-invocation {@link OtelSpanSink} over a shared {@link Tracer}. Lives in
 * the access layer (not the neutral SPI) because it depends on the optional OpenTelemetry
 * API; it is only instantiated by the OTel-conditional boot configuration, so the OTel
 * classpath is required only when trajectory OTel export is switched on.
 */
public final class OtelSpanSinkFactory implements TrajectorySinkFactory {

    private final Tracer tracer;

    public OtelSpanSinkFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public TrajectorySink create() {
        return new OtelSpanSink(tracer);
    }
}
