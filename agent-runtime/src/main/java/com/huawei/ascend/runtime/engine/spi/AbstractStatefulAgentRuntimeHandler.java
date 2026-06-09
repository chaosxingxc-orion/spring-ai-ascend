package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStatefulAgentRuntimeHandler.class);

    protected AbstractStatefulAgentRuntimeHandler(String agentId, String name, String description) {
        super(agentId, name, description);
    }

    protected AbstractStatefulAgentRuntimeHandler(
            String agentId, String name, String description, String version, String endpoint) {
        super(agentId, name, description, version, endpoint);
    }

    @Override
    public final Stream<?> execute(AgentExecutionContext context) {
        beforeExecute(context);
        try {
            Stream<?> results = Objects.requireNonNull(doExecute(context), "doExecute result stream");
            return results.onClose(() -> safeAfterExecute(context));
        } catch (RuntimeException ex) {
            safeAfterExecute(context);
            throw ex;
        }
    }

    private void safeAfterExecute(AgentExecutionContext context) {
        try {
            afterExecute(context);
        } catch (RuntimeException ex) {
            LOGGER.warn("agent state export hook failed tenantId={} sessionId={} taskId={} agentId={} errorClass={} message={}",
                    context.getScope().tenantId(),
                    context.getScope().sessionId(),
                    context.getScope().taskId(),
                    context.getScope().agentId(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
        }
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
