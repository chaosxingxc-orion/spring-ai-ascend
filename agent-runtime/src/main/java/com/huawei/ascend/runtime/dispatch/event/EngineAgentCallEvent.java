package com.huawei.ascend.runtime.dispatch.event;

import com.huawei.ascend.runtime.dispatch.model.AgentCallMode;
import com.huawei.ascend.runtime.dispatch.model.EngineInput;

/**
 * Emitted when an agent invokes another agent. See engine model design §6.5.
 * Routing of this event is handled from Phase 3 onward.
 */
public class EngineAgentCallEvent extends EngineExecutionEvent {
    private String parentAgentId;
    private String targetAgentId;
    private AgentCallMode mode;
    private EngineInput input;

    public EngineAgentCallEvent() {
    }

    public String getParentAgentId() {
        return parentAgentId;
    }

    public void setParentAgentId(String parentAgentId) {
        this.parentAgentId = parentAgentId;
    }

    public String getTargetAgentId() {
        return targetAgentId;
    }

    public void setTargetAgentId(String targetAgentId) {
        this.targetAgentId = targetAgentId;
    }

    public AgentCallMode getMode() {
        return mode;
    }

    public void setMode(AgentCallMode mode) {
        this.mode = mode;
    }

    public EngineInput getInput() {
        return input;
    }

    public void setInput(EngineInput input) {
        this.input = input;
    }
}
