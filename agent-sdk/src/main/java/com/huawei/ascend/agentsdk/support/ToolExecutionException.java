package com.huawei.ascend.agentsdk.support;

/** A resolved tool failed at invocation time (transport error, non-2xx, unsupported transport). */
public class ToolExecutionException extends AgentSdkException {
    public ToolExecutionException(String message) {
        super(message);
    }

    public ToolExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
