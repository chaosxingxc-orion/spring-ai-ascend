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
                HttpToolResolver.descriptor(spec),
                new McpExecutionHandle(
                        HttpToolResolver.required(attributes, "server"),
                        HttpToolResolver.required(attributes, "tool")));
    }
}

