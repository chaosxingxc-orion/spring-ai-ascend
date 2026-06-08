package com.huawei.ascend.runtime.engine;

import java.time.Instant;

/**
 * Emitted when an agent execution starts. See engine model design §6.3.
 */
public class EngineStartedEvent extends EngineExecutionEvent {
    public EngineStartedEvent() {
    }

    public EngineStartedEvent(String eventId, EngineExecutionScope scope, Instant occurredAt) {
        super(eventId, scope, occurredAt);
    }
}
