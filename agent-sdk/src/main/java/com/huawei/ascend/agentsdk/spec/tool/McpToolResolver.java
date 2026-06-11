package com.huawei.ascend.agentsdk.spec.tool;

import java.util.Map;

public final class McpToolResolver implements ToolResolver {
    @Override
    public boolean supports(String scheme) {
        return "mcp".equalsIgnoreCase(scheme);
    }

    @Override
    public ResolvedTool resolve(ToolSpec spec) {
        Map<String, Object> attributes = spec.ref().attributes();
        return new WrappableTool(
                ToolRefAttributes.descriptor(spec),
                new McpExecutionHandle(
                        ToolRefAttributes.required(attributes, "server"),
                        ToolRefAttributes.required(attributes, "tool")));
    }
}

