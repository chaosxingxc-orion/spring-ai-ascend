package com.huawei.ascend.examples.a2a.gateway.model;

public record GatewayHealthSnapshot(
        int registeredRuntimeCount,
        int readyRuntimeCount,
        int unreachableRuntimeCount,
        long telemetryEventCount) {
}
