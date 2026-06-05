package com.huawei.ascend.examples.a2a.gateway.model;

import java.time.Duration;

public record SlaSnapshot(
        Duration serviceRoutingLatency,
        Duration runtimeAdmissionLatency,
        Duration runtimeModelFirstTokenLatency,
        boolean firstTokenSlaBreached) {

    public static SlaSnapshot empty() {
        return new SlaSnapshot(Duration.ZERO, Duration.ZERO, Duration.ZERO, false);
    }
}
