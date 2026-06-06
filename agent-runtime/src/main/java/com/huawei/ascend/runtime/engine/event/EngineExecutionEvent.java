package com.huawei.ascend.runtime.engine.event;

import com.huawei.ascend.runtime.engine.model.EngineExecutionScope;
import java.time.Instant;

/**
 * Base type for all engine execution events emitted by an {@code AgentRuntimeHandler}.
 * See engine model design §6.2.
 */
public abstract class EngineExecutionEvent {
    private String eventId;
    private EngineExecutionScope scope;
    private Instant occurredAt;

    protected EngineExecutionEvent() {
    }

    protected EngineExecutionEvent(String eventId, EngineExecutionScope scope, Instant occurredAt) {
        this.eventId = eventId;
        this.scope = scope;
        this.occurredAt = occurredAt;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public EngineExecutionScope getScope() {
        return scope;
    }

    public void setScope(EngineExecutionScope scope) {
        this.scope = scope;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
