package com.huawei.ascend.runtime.engine.port;

import com.huawei.ascend.runtime.engine.event.EngineCancelledEvent;
import com.huawei.ascend.runtime.engine.event.EngineCompletedEvent;
import com.huawei.ascend.runtime.engine.event.EngineFailedEvent;
import com.huawei.ascend.runtime.engine.event.EngineInterruptedEvent;
import com.huawei.ascend.runtime.engine.event.EngineOutputEvent;
import com.huawei.ascend.runtime.engine.model.EngineExecutionScope;

/**
 * The engine's single outbound port. The engine reports every execution outcome —
 * lifecycle transitions AND streaming output — through this one client; the
 * control plane is the sole authority and is responsible for fanning out
 * caller-facing egress, so the engine never writes authority and output twice.
 */
public interface TaskControlClient {

    void markRunning(EngineExecutionScope scope);

    /** Streaming output chunk; the control plane forwards it to egress while the task is still live. */
    void appendOutput(EngineExecutionScope scope, EngineOutputEvent event);

    void markWaiting(EngineExecutionScope scope, EngineInterruptedEvent event);

    void markSucceeded(EngineExecutionScope scope, EngineCompletedEvent event);

    void markFailed(EngineExecutionScope scope, EngineFailedEvent event);

    void markCancelled(EngineExecutionScope scope, EngineCancelledEvent event);
}
