package com.huawei.ascend.agentsdk.support;

public class AgentSdkException extends RuntimeException {
    public AgentSdkException(String message) {
        super(message);
    }

    public AgentSdkException(String message, Throwable cause) {
        super(message, cause);
    }
}

