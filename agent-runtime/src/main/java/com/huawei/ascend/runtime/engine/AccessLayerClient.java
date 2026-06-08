package com.huawei.ascend.runtime.engine;


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
