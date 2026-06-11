package com.huawei.ascend.agentsdk.adapter.deepagent;

import com.huawei.ascend.agentsdk.support.OptionValues;
import java.util.Map;

public record OpenJiuwenDeepAgentOptions(
        int maxIterations) {

    public static OpenJiuwenDeepAgentOptions from(Map<String, Object> options) {
        int maxIterations = OptionValues.intOption(options, "maxIterations", 15);
        return new OpenJiuwenDeepAgentOptions(maxIterations);
    }
}
