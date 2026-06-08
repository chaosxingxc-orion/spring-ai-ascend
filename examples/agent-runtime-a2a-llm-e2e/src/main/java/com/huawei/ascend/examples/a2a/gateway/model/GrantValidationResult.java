package com.huawei.ascend.examples.a2a.gateway.model;

public record GrantValidationResult(
        boolean accepted,
        GatewayErrorCode code,
        String message) {

    public static GrantValidationResult success() {
        return new GrantValidationResult(true, null, "accepted");
    }

    public static GrantValidationResult rejected(GatewayErrorCode code, String message) {
        return new GrantValidationResult(false, code, message);
    }
}
