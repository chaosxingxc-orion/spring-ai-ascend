package com.huawei.ascend.examples.a2a.gateway.model;

import java.net.URI;

public record AgentCardSummary(
        String tenantId,
        String agentId,
        String name,
        String version,
        URI a2aEndpoint,
        RuntimeState state) {
}
