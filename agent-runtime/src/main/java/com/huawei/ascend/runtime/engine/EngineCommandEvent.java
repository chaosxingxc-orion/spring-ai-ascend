package com.huawei.ascend.runtime.engine;

import java.time.Instant;

/**
 * A command placed on the engine's internal command queue. Carries the intent
 * ({@code commandType}: EXECUTE / RESUME / CANCEL) plus the scope and input.
 * See engine model design §6.1.
 */
public class EngineCommandEvent {
    private String commandType;
    private EngineExecutionScope scope;
    private EngineInput input;
    private Instant createdAt;

    public EngineCommandEvent() {
    }

    public EngineCommandEvent(String commandType, EngineExecutionScope scope, EngineInput input, Instant createdAt) {
        this.commandType = commandType;
        this.scope = scope;
        this.input = input;
        this.createdAt = createdAt;
    }

    public String getCommandType() {
        return commandType;
    }

    public void setCommandType(String commandType) {
        this.commandType = commandType;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
