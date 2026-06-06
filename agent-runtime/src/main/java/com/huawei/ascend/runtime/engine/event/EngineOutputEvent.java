package com.huawei.ascend.runtime.engine.event;

import com.huawei.ascend.runtime.engine.model.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.model.EngineOutput;
import java.time.Instant;

/**
 * Emitted for an incremental piece of agent output. See engine model design §6.4.
 */
public class EngineOutputEvent extends EngineExecutionEvent {
    private EngineOutput output;

    public EngineOutputEvent() {
    }

    public EngineOutputEvent(String eventId, EngineExecutionScope scope, Instant occurredAt, EngineOutput output) {
        super(eventId, scope, occurredAt);
        this.output = output;
    }

    public EngineOutput getOutput() {
        return output;
    }

    public void setOutput(EngineOutput output) {
        this.output = output;
    }
}
