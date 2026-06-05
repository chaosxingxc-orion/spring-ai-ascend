package com.huawei.ascend.examples.a2a.gateway.model;

import java.util.Map;

public record RoutingContext(
        String sessionId,
        String correlationId,
        Map<String, Object> metadata) {

    public RoutingContext {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static RoutingContext empty() {
        return new RoutingContext(null, null, Map.of());
    }
}
