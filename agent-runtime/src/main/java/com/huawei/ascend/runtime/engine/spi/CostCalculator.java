package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Usage;

/**
 * Fills the {@code provider} and {@code costMicros} of a model-call {@link Usage} from a pricing
 * model, so FinOps/billing dashboards can aggregate spend without each consumer re-deriving cost.
 * Applied by the runtime when stamping a model-call event; a usage whose model is not priced (or a
 * null usage) is returned unchanged. Not an SPI — the runtime ships a config-table implementation;
 * this interface only decouples the emitter from that implementation.
 */
@FunctionalInterface
public interface CostCalculator {

    /** A no-op calculator: leaves provider/costMicros untouched. The default when pricing is off. */
    CostCalculator NONE = usage -> usage;

    Usage enrich(Usage usage);
}
