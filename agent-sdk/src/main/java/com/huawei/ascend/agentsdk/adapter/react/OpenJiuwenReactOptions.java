package com.huawei.ascend.agentsdk.adapter.react;

import java.util.Map;

public record OpenJiuwenReactOptions(
        int maxIterations,
        String sysOperationId,
        String executeMode) {

    public static OpenJiuwenReactOptions from(Map<String, Object> options, String defaultId) {
        Object iterations = options.get("maxIterations");
        int maxIterations = iterations instanceof Number number ? number.intValue() : 5;
        String sysOperationId = value(options.get("sysOperationId"), defaultId);
        String executeMode = value(options.get("executeMode"), "openjiuwen");
        return new OpenJiuwenReactOptions(maxIterations, sysOperationId, executeMode);
    }

    private static String value(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }
}

