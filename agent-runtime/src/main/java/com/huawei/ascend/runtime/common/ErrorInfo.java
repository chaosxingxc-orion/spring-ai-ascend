package com.huawei.ascend.runtime.common;

public record ErrorInfo(String code, String message) {

    public ErrorInfo {
        code = normalize(code, "UNKNOWN");
        message = message == null ? "" : message;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
