package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;

/**
 * Optional provider attached to an {@link AgentRuntimeHandler}.
 *
 * <p>Providers are deliberately small lifecycle hooks. They let agent adapters
 * compose features such as Agent State restore/export, sandbox preparation,
 * tool-call overrides, and tracing without creating a deep stack of abstract
 * base classes.
 */
public interface AgentRuntimeProvider {

    /** Prepare the execution context before the concrete handler runs. */
    default void beforeExecute(AgentExecutionContext context) {
    }

    /** Export side effects back to the execution context after results finish. */
    default void afterExecute(AgentExecutionContext context) {
    }
}
