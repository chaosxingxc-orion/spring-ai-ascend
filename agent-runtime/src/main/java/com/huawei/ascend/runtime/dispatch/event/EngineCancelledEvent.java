package com.huawei.ascend.service.engine.event;

import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import java.time.Instant;

/**
 * Emitted when an agent execution is cancelled. See engine model design §6.9.
 */
public class EngineCancelledEvent extends EngineExecutionEvent {
    private String reason;

    public EngineCancelledEvent() {
    }

    public EngineCancelledEvent(String eventId, EngineExecutionScope scope, Instant occurredAt, String reason) {
        super(eventId, scope, occurredAt);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
