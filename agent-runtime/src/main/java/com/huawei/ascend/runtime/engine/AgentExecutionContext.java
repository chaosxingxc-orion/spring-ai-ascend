package com.huawei.ascend.runtime.engine;

import com.huawei.ascend.runtime.engine.service.AgentStateKey;
import com.huawei.ascend.runtime.engine.service.AgentStateSnapshot;
import java.util.Map;
import java.util.Optional;

/**
 * The context handed to an {@code AgentRuntimeHandler} for a single execution:
 * the scope, input, and framework-neutral Agent state checkpoint owned by the
 * runtime.
 */
public class AgentExecutionContext {
    private EngineExecutionScope scope;
    private EngineInput input;
    private AgentStateSnapshot agentState;

    public AgentExecutionContext() {
    }

    public AgentExecutionContext(EngineExecutionScope scope, EngineInput input) {
        this(scope, input, null);
    }

    public AgentExecutionContext(EngineExecutionScope scope, EngineInput input, AgentStateSnapshot agentState) {
        this.scope = scope;
        this.input = input;
        this.agentState = agentState;
    }

    public EngineExecutionScope getScope() {
        return scope;
    }

    public void setScope(EngineExecutionScope scope) {
        this.scope = scope;
    }

    public EngineInput getInput() {
        return input;
    }

    public void setInput(EngineInput input) {
        this.input = input;
    }

    public Optional<AgentStateSnapshot> getAgentState() {
        return Optional.ofNullable(agentState);
    }

    public void setAgentState(AgentStateSnapshot agentState) {
        this.agentState = agentState;
    }

    /**
     * Replaces the state payload for this task and increments the checkpoint
     * revision.
     *
     * <p>Framework adapters should use this for runtime execution state only.
     * Domain state such as order/payment status belongs to the business system
     * and should be referenced here rather than copied wholesale.
     */
    public AgentStateSnapshot replaceAgentState(Map<String, Object> values) {
        AgentStateKey key = agentState == null ? AgentStateKey.from(scope) : agentState.key();
        AgentStateSnapshot next = agentState == null
                ? AgentStateSnapshot.empty(key).next(values)
                : agentState.next(values);
        this.agentState = next;
        return next;
    }
}
