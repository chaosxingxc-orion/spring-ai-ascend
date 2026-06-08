package com.huawei.ascend.runtime.access.output;

import java.util.Objects;

public record RuntimeOutputHandle(String tenantId, String sessionId, String taskId) {

    public RuntimeOutputHandle {
        tenantId = requireNonBlank(tenantId, "tenantId");
        sessionId = requireNonBlank(sessionId, "sessionId");
        taskId = requireNonBlank(taskId, "taskId");
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
