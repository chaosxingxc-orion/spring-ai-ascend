package com.huawei.ascend.agentsdk.adapter.react;

import com.huawei.ascend.agentsdk.support.OptionValues;
import java.util.Map;

public record OpenJiuwenReactOptions(
        int maxIterations,
        String sysOperationId,
        String executeMode) {

    public static OpenJiuwenReactOptions from(Map<String, Object> options, String defaultId) {
        int maxIterations = OptionValues.intOption(options, "maxIterations", 5);
        String sysOperationId = value(options.get("sysOperationId"), defaultId);
        String executeMode = value(options.get("executeMode"), "openjiuwen");
        return new OpenJiuwenReactOptions(maxIterations, sysOperationId, executeMode);
    }

    private static String value(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }
}

