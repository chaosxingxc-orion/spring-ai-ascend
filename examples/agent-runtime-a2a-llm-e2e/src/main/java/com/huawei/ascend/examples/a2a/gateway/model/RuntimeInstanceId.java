package com.huawei.ascend.examples.a2a.gateway.model;

import java.util.Objects;

public record RuntimeInstanceId(String value) {

    public RuntimeInstanceId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("runtimeInstanceId is required");
        }
        value = value.trim();
    }

    public static RuntimeInstanceId of(String value) {
        return new RuntimeInstanceId(value);
    }

    @Override
    public String toString() {
        return Objects.toString(value);
    }
}
