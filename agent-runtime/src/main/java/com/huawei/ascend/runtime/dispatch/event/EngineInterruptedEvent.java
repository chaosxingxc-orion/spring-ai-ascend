package com.huawei.ascend.service.engine.event;

import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import com.huawei.ascend.service.engine.model.InterruptType;
import java.time.Instant;

/**
 * Emitted when an agent execution pauses and waits (e.g. for human input or
 * approval). See engine model design §6.6.
 */
public class EngineInterruptedEvent extends EngineExecutionEvent {
    private InterruptType interruptType;
    private String prompt;

    public EngineInterruptedEvent() {
    }

    public EngineInterruptedEvent(String eventId, EngineExecutionScope scope, Instant occurredAt, InterruptType interruptType, String prompt) {
        super(eventId, scope, occurredAt);
        this.interruptType = interruptType;
        this.prompt = prompt;
    }

    public InterruptType getInterruptType() {
        return interruptType;
    }

    public void setInterruptType(InterruptType interruptType) {
        this.interruptType = interruptType;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
}
