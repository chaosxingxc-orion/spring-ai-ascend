package com.huawei.ascend.agentsdk.spec.tool;

import java.util.Map;

public final class JavaFileToolResolver implements ToolResolver {
    @Override
    public boolean supports(String scheme) {
        return "file".equalsIgnoreCase(scheme);
    }

    @Override
    public ResolvedTool resolve(ToolSpec spec) {
        Map<String, Object> attributes = spec.ref().attributes();
        return new WrappableTool(
                HttpToolResolver.descriptor(spec),
                new JavaExecutionHandle(
                        HttpToolResolver.required(attributes, "class"),
                        HttpToolResolver.required(attributes, "method")));
    }
}
