package com.huawei.ascend.runtime.engine;


/**
 * Outbound port to the access layer. The engine streams output and terminal
 * signals back to the caller-facing channel through this client.
 */
public interface AccessLayerClient {

    void appendOutput(EngineExecutionScope scope, EngineEvent event);

    void completeOutput(EngineExecutionScope scope, EngineEvent event);

    void failOutput(EngineExecutionScope scope, EngineEvent event);

    void requestUserInput(EngineExecutionScope scope, EngineEvent event);
}
