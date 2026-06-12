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
        if (attributes.containsKey("path")) {
            throw new UnsupportedToolRefException("file tool ref does not support path; use class and method");
        }
        return new WrappableTool(
                ToolRefAttributes.descriptor(spec),
                new JavaExecutionHandle(
                        ToolRefAttributes.required(attributes, "class"),
                        ToolRefAttributes.required(attributes, "method")));
    }
}
