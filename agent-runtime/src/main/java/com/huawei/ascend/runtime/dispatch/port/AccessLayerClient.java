package com.huawei.ascend.service.engine.port;

import com.huawei.ascend.service.engine.event.EngineCompletedEvent;
import com.huawei.ascend.service.engine.event.EngineFailedEvent;
import com.huawei.ascend.service.engine.event.EngineInterruptedEvent;
import com.huawei.ascend.service.engine.event.EngineOutputEvent;
import com.huawei.ascend.service.engine.model.EngineExecutionScope;

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
