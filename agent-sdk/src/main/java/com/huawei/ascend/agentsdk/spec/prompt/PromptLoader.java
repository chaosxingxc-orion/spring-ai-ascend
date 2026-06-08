package com.huawei.ascend.agentsdk.spec.prompt;

public final class PromptLoader {
    public PromptSpec inlineSystem(String system) {
        return new PromptSpec(system == null ? "" : system);
    }
}

