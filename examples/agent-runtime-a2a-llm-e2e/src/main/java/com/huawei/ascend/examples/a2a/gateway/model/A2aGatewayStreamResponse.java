package com.huawei.ascend.examples.a2a.gateway.model;

import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;

public record A2aGatewayStreamResponse(
        int statusCode,
        String contentType,
        Duration routeResolveLatency,
        Duration firstByteLatency,
        String runtimeInstanceId,
        InputStream body) {

    public A2aGatewayStreamResponse {
        routeResolveLatency = routeResolveLatency == null ? Duration.ZERO : routeResolveLatency;
        firstByteLatency = firstByteLatency == null ? Duration.ZERO : firstByteLatency;
        runtimeInstanceId = runtimeInstanceId == null ? "" : runtimeInstanceId;
        body = Objects.requireNonNull(body, "body");
    }
}
