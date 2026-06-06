package com.huawei.ascend.runtime.engine.event;

import com.huawei.ascend.runtime.engine.model.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.model.EngineOutput;
import java.time.Instant;

/**
 * Emitted when an agent execution completes successfully, carrying the final
 * output. See engine model design §6.7.
 */
public class EngineCompletedEvent extends EngineExecutionEvent {
    private EngineOutput finalOutput;

    public EngineCompletedEvent() {
    }

    public EngineCompletedEvent(String eventId, EngineExecutionScope scope, Instant occurredAt, EngineOutput finalOutput) {
        super(eventId, scope, occurredAt);
        this.finalOutput = finalOutput;
    }

    public EngineOutput getFinalOutput() {
        return finalOutput;
    }

    public void setFinalOutput(EngineOutput finalOutput) {
        this.finalOutput = finalOutput;
    }
}
