package com.huawei.ascend.agentsdk.spec.tool;

public interface ToolResolver {
    boolean supports(String scheme);

    ResolvedTool resolve(ToolSpec spec);
}

