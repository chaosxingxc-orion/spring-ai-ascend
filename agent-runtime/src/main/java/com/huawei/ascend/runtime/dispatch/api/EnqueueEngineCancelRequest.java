package com.huawei.ascend.service.engine.api;

import com.huawei.ascend.service.engine.model.EngineExecutionScope;

import java.util.Objects;

/**
 * Request to cancel an Agent execution.
 *
 * <p>Used to cancel the execution for the specified scope. Only responsible
 * for sending the cancel command. The cancellation result is written back
 * through TaskControlClient.
 *
 * @param scope execution scope (identifies the execution to cancel).
 */
public record EnqueueEngineCancelRequest(
        EngineExecutionScope scope) {

    public EnqueueEngineCancelRequest {
        Objects.requireNonNull(scope, "scope");
    }
}
