package com.huawei.ascend.agentsdk.adapter.deepagent;

import com.huawei.ascend.agentsdk.support.OptionValues;
import java.util.Map;

public record OpenJiuwenDeepAgentOptions(
        int maxIterations,
        String executeMode) {

    public static OpenJiuwenDeepAgentOptions from(Map<String, Object> options) {
        int maxIterations = OptionValues.intOption(options, "maxIterations", 15);
        String executeMode = options.get("executeMode") == null ? "openjiuwen" : String.valueOf(options.get("executeMode"));
        return new OpenJiuwenDeepAgentOptions(maxIterations, executeMode);
    }
}

