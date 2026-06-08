package com.huawei.ascend.agentsdk.spec.tool;

public sealed interface ExecutionHandle
        permits HttpExecutionHandle, JavaExecutionHandle, McpExecutionHandle {
}

