package com.huawei.ascend.agentsdk.spec.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolResolverTest {

    @Test
    void javaFileResolverCreatesWrappableJavaToolFromAgentToolMetadata() {
        ToolSpec spec = new ToolSpec(
                "inventoryLookup",
                "lookup inventory",
                Map.of("type", "object"),
                Map.of("type", "object"),
                ToolRef.of("file", Map.of(
                        "class", "example.OrderTools",
                        "method", "query")),
                false);

        ResolvedTool resolved = new JavaFileToolResolver().resolve(spec);

        assertThat(resolved).isInstanceOf(WrappableTool.class);
        WrappableTool tool = (WrappableTool) resolved;
        assertThat(tool.descriptor().name()).isEqualTo("inventoryLookup");
        assertThat(tool.descriptor().description()).isEqualTo("lookup inventory");
        assertThat(tool.executionHandle()).isInstanceOf(JavaExecutionHandle.class);
        JavaExecutionHandle handle = (JavaExecutionHandle) tool.executionHandle();
        assertThat(handle.className()).isEqualTo("example.OrderTools");
        assertThat(handle.methodName()).isEqualTo("query");
    }

    @Test
    void javaFileResolverDoesNotRequirePathForExecution() {
        ToolSpec spec = new ToolSpec(
                "inventoryLookup",
                "lookup inventory",
                Map.of("type", "object"),
                Map.of("type", "object"),
                ToolRef.of("file", Map.of(
                        "class", "example.OrderTools",
                        "method", "query")),
                false);

        ResolvedTool resolved = new JavaFileToolResolver().resolve(spec);

        assertThat(resolved).isInstanceOf(WrappableTool.class);
        JavaExecutionHandle handle = (JavaExecutionHandle) ((WrappableTool) resolved).executionHandle();
        assertThat(handle.className()).isEqualTo("example.OrderTools");
        assertThat(handle.methodName()).isEqualTo("query");
    }
}

