package com.huawei.ascend.service.access.protocol.a2a.egress;

import java.util.Map;
import org.a2aproject.sdk.spec.StreamingEventKind;

public record A2aOutput(
        String kind,
        String taskId,
        StreamingEventKind event,
        Object body,
        boolean terminal,
        Map<String, Object> metadata) {

    public A2aOutput {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}


