package com.huawei.ascend.runtime.engine;


/**
 * The engine's single outbound port. The engine reports every execution outcome —
 * lifecycle transitions AND streaming output — through this one client; the
 * control plane is the sole authority and is responsible for fanning out
 * caller-facing egress, so the engine never writes authority and output twice.
 */
public interface TaskControlClient {

    void markRunning(EngineExecutionScope scope);

    /** Streaming output chunk; the control plane forwards it to egress while the task is still live. */
    void appendOutput(EngineExecutionScope scope, EngineEvent event);

    void markWaiting(EngineExecutionScope scope, EngineEvent event);

    void markSucceeded(EngineExecutionScope scope, EngineEvent event);

    void markFailed(EngineExecutionScope scope, EngineEvent event);

    void markCancelled(EngineExecutionScope scope, EngineEvent event);
}
