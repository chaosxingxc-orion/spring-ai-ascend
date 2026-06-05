package com.huawei.ascend.examples.a2a.gateway.model;

public record RuntimeDeregisterResult(
        RuntimeInstanceId runtimeInstanceId,
        RuntimeState state,
        boolean removed) {
}
