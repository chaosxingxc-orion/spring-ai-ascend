package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Optional base class for Agent framework adapters that need runtime Agent
 * State restore/export hooks.
 *
 * <p>This class deliberately does not hold {@code AgentStateStore}. The engine
 * dispatcher loads state into {@link AgentExecutionContext} before execution and
 * saves context state after the returned stream is closed. Subclasses only
 * translate between that context state and their concrete Agent framework.
 */
public abstract class AbstractStatefulAgentRuntimeHandler extends AbstractAgentRuntimeHandler {

    protected AbstractStatefulAgentRuntimeHandler(String agentId, String name, String description) {
        super(agentId, name, description);
        addRuntimeExtension(stateExtension());
    }

    protected AbstractStatefulAgentRuntimeHandler(
            String agentId, String name, String description, String version, String endpoint) {
        super(agentId, name, description, version, endpoint);
        addRuntimeExtension(stateExtension());
    }

    @Override
    public final Stream<?> execute(AgentExecutionContext context) {
        return Objects.requireNonNull(doExecute(context), "doExecute result stream");
    }

    private AgentRuntimeExtension stateExtension() {
        return new AgentRuntimeExtension() {
            @Override
            public void beforeExecute(AgentExecutionContext context) {
                AbstractStatefulAgentRuntimeHandler.this.beforeExecute(context);
            }

            @Override
            public void afterExecute(AgentExecutionContext context) {
                AbstractStatefulAgentRuntimeHandler.this.afterExecute(context);
            }
        };
    }

    /** Restore framework state from the already-loaded execution context. */
    protected void beforeExecute(AgentExecutionContext context) {
    }

    /** Export framework state back into the execution context. */
    protected void afterExecute(AgentExecutionContext context) {
    }

    /** Run the concrete Agent framework and return framework-specific results. */
    protected abstract Stream<?> doExecute(AgentExecutionContext context);
}
