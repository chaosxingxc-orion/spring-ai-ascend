package com.huawei.ascend.agentsdk.spec.tool;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record HttpExecutionHandle(
        URI url,
        String method,
        Map<String, String> headers,
        Duration timeout) implements ExecutionHandle {

    public HttpExecutionHandle {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }
}

