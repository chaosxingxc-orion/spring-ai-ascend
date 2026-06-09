package com.huawei.ascend.agentsdk.support;

public class ValidationException extends AgentSdkException {
    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

