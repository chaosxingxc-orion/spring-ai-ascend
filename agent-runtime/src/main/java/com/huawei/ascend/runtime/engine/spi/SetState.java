package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.Map;

/**
 * Minimal state-write SPI for frameworks that need an explicit state bridge.
 *
 * <p>Frameworks with a native checkpointer can ignore this SPI. Frameworks
 * without one may use it to publish a framework-neutral state map back to the
 * runtime execution context or to a caller-owned state store.
 */
@FunctionalInterface
public interface SetState {

    /** Write the current framework state for the supplied execution context. */
    void setState(AgentExecutionContext context, Map<String, Object> values);
}
