package com.huawei.ascend.runtime.engine.port;

import com.huawei.ascend.runtime.engine.event.EngineCancelledEvent;
import com.huawei.ascend.runtime.engine.event.EngineCompletedEvent;
import com.huawei.ascend.runtime.engine.event.EngineFailedEvent;
import com.huawei.ascend.runtime.engine.event.EngineInterruptedEvent;
import com.huawei.ascend.runtime.engine.model.EngineExecutionScope;

/**
 * Outbound port to the task-centric control plane. The engine reports task
 * lifecycle transitions through this client. See engine model design §11.1.
 */
public interface TaskControlClient {

    void markRunning(EngineExecutionScope scope);

    void markWaiting(EngineExecutionScope scope, EngineInterruptedEvent event);

    void markSucceeded(EngineExecutionScope scope, EngineCompletedEvent event);

    void markFailed(EngineExecutionScope scope, EngineFailedEvent event);

    void markCancelled(EngineExecutionScope scope, EngineCancelledEvent event);
}
