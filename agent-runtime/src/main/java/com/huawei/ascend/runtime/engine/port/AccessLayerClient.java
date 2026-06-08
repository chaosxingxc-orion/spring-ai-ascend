package com.huawei.ascend.runtime.engine.port;

import com.huawei.ascend.runtime.engine.event.EngineCompletedEvent;
import com.huawei.ascend.runtime.engine.event.EngineFailedEvent;
import com.huawei.ascend.runtime.engine.event.EngineInterruptedEvent;
import com.huawei.ascend.runtime.engine.event.EngineOutputEvent;
import com.huawei.ascend.runtime.engine.model.EngineExecutionScope;

/**
 * Outbound port to the access layer. The engine streams output and terminal
 * signals back to the caller-facing channel through this client.
 * See engine model design §11.2.
 */
public interface AccessLayerClient {

    void appendOutput(EngineExecutionScope scope, EngineOutputEvent event);

    void completeOutput(EngineExecutionScope scope, EngineCompletedEvent event);

    void failOutput(EngineExecutionScope scope, EngineFailedEvent event);

    void requestUserInput(EngineExecutionScope scope, EngineInterruptedEvent event);
}
