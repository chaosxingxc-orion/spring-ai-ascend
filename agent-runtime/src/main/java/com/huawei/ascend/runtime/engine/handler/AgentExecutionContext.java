package com.huawei.ascend.runtime.engine.handler;

import com.huawei.ascend.runtime.engine.model.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.model.EngineInput;

/**
 * The context handed to an {@code AgentRuntimeHandler} for a single execution: the
 * scope (who/what is running) plus the input. See engine model design §9.2.
 */
public class AgentExecutionContext {
    private EngineExecutionScope scope;
    private EngineInput input;

    public AgentExecutionContext() {
    }

    public AgentExecutionContext(EngineExecutionScope scope, EngineInput input) {
        this.scope = scope;
        this.input = input;
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
}
