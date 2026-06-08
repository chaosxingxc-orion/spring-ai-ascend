package com.huawei.ascend.runtime.engine.event;

import com.huawei.ascend.runtime.engine.model.EngineExecutionScope;
import java.time.Instant;

/**
 * Emitted when an agent execution fails. See engine model design §6.8.
 */
public class EngineFailedEvent extends EngineExecutionEvent {
    private String errorCode;
    private String errorMessage;

    public EngineFailedEvent() {
    }

    public EngineFailedEvent(String eventId, EngineExecutionScope scope, Instant occurredAt, String errorCode, String errorMessage) {
        super(eventId, scope, occurredAt);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
