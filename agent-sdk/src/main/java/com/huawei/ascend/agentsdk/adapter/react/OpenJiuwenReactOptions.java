package com.huawei.ascend.agentsdk.adapter.react;

import com.huawei.ascend.agentsdk.support.OptionValues;
import java.util.Map;

public record OpenJiuwenReactOptions(
        int maxIterations,
        String sysOperationId) {

    public static OpenJiuwenReactOptions from(Map<String, Object> options, String defaultId) {
        int maxIterations = OptionValues.intOption(options, "maxIterations", 5);
        String sysOperationId = value(options.get("sysOperationId"), defaultId);
        return new OpenJiuwenReactOptions(maxIterations, sysOperationId);
    }

    private static String value(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }
}
