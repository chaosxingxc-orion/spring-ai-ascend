package com.huawei.ascend.examples.a2a.gateway.model;

import java.net.URI;
import java.time.Instant;

public record RuntimeRoute(
        String agentId,
        RuntimeInstanceId runtimeInstanceId,
        URI a2aEndpoint,
        RuntimeState state,
        Instant lastHeartbeatAt,
        SlaSnapshot slaSnapshot) {
}
