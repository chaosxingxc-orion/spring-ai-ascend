package com.huawei.ascend.agentsdk.spec.tool;

public record WrappableTool(
        ToolDescriptor descriptor,
        ExecutionHandle executionHandle) implements ResolvedTool {
}

