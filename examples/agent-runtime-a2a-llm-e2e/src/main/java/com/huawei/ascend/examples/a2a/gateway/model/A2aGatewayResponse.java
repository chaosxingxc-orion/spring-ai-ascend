package com.huawei.ascend.examples.a2a.gateway.model;

import java.time.Duration;

public record A2aGatewayResponse(
        int statusCode,
        String contentType,
        Duration routeResolveLatency,
        Duration firstByteLatency,
        Duration forwardLatency,
        String runtimeInstanceId,
        byte[] body) {

    public A2aGatewayResponse {
        routeResolveLatency = routeResolveLatency == null ? Duration.ZERO : routeResolveLatency;
        firstByteLatency = firstByteLatency == null ? Duration.ZERO : firstByteLatency;
        forwardLatency = forwardLatency == null ? Duration.ZERO : forwardLatency;
        runtimeInstanceId = runtimeInstanceId == null ? "" : runtimeInstanceId;
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
