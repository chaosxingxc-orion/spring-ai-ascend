package com.huawei.ascend.runtime.engine.api;

import com.huawei.ascend.runtime.engine.model.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.model.EngineInput;

import java.util.Objects;

/**
 * Request to resume an interrupted Agent execution.
 *
 * <p>Used to resume an Agent execution that was previously interrupted.
 * The {@code scope} locates the original task/session/agent. The {@code input}
 * represents manual input, approval result, or child agent return result.
 *
 * @param scope execution scope (locates the original task/session/agent).
 * @param input resume input (manual input, approval result, or child agent result).
 */
public record EnqueueEngineResumeRequest(
        EngineExecutionScope scope,
        EngineInput input) {

    public EnqueueEngineResumeRequest {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(input, "input");
    }
}
