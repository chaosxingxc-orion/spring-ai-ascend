package com.huawei.ascend.runtime.access.output;

import com.huawei.ascend.runtime.common.Guards;

public record RuntimeOutputHandle(String tenantId, String sessionId, String taskId) {

    public RuntimeOutputHandle {
        tenantId = Guards.requireNonBlank(tenantId, "tenantId");
        sessionId = Guards.requireNonBlank(sessionId, "sessionId");
        taskId = Guards.requireNonBlank(taskId, "taskId");
    }
}
