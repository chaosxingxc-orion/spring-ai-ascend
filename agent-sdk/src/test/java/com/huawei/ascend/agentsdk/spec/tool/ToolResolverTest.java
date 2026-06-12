package com.huawei.ascend.agentsdk.spec.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolResolverTest {

    @Test
    void javaFileResolverCreatesWrappableJavaToolFromAgentToolMetadata() {
        ToolSpec spec = new ToolSpec(
                "inventoryLookup",
                "lookup inventory",
                Map.of("type", "object"),
                ToolRef.of("file", Map.of(
                        "class", "example.OrderTools",
                        "method", "query")));

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

    /** Bare numbers are SECONDS — 30 must not silently become a 30 ms deadline. */
    @Test
    void timeoutBareNumberMeansSeconds() {
        HttpExecutionHandle handle = resolveHttp(Map.of("url", "https://api.example.com", "timeout", 30));

        assertThat(handle.timeout()).isEqualTo(java.time.Duration.ofSeconds(30));
    }

    @Test
    void timeoutAcceptsHumanSuffixForms() {
        assertThat(resolveHttp(Map.of("url", "https://api.example.com", "timeout", "45s")).timeout())
                .isEqualTo(java.time.Duration.ofSeconds(45));
        assertThat(resolveHttp(Map.of("url", "https://api.example.com", "timeout", "500ms")).timeout())
                .isEqualTo(java.time.Duration.ofMillis(500));
        assertThat(resolveHttp(Map.of("url", "https://api.example.com", "timeout", "2m")).timeout())
                .isEqualTo(java.time.Duration.ofMinutes(2));
        assertThat(resolveHttp(Map.of("url", "https://api.example.com", "timeout", "PT90S")).timeout())
                .isEqualTo(java.time.Duration.ofSeconds(90));
    }

    @Test
    void garbageTimeoutAndUrlFailWithValidationException() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> resolveHttp(Map.of("url", "https://api.example.com", "timeout", "soon")))
                .isInstanceOf(com.huawei.ascend.agentsdk.support.ValidationException.class)
                .hasMessageContaining("timeout");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> resolveHttp(Map.of("url", "ht tp://broken url")))
                .isInstanceOf(com.huawei.ascend.agentsdk.support.ValidationException.class)
                .hasMessageContaining("url");
    }

    private static HttpExecutionHandle resolveHttp(Map<String, Object> attributes) {
        ToolSpec spec = new ToolSpec(
                "httpTool", "http tool", Map.of(), ToolRef.of("http", attributes));
        return (HttpExecutionHandle) ((WrappableTool) new HttpToolResolver().resolve(spec)).executionHandle();
    }

    @Test
    void javaFileResolverDoesNotRequirePathForExecution() {
        ToolSpec spec = new ToolSpec(
                "inventoryLookup",
                "lookup inventory",
                Map.of("type", "object"),
                ToolRef.of("file", Map.of(
                        "class", "example.OrderTools",
                        "method", "query")));

        ResolvedTool resolved = new JavaFileToolResolver().resolve(spec);

        assertThat(resolved).isInstanceOf(WrappableTool.class);
        JavaExecutionHandle handle = (JavaExecutionHandle) ((WrappableTool) resolved).executionHandle();
        assertThat(handle.className()).isEqualTo("example.OrderTools");
        assertThat(handle.methodName()).isEqualTo("query");
    }

    @Test
    void javaFileResolverRejectsPathBecauseExecutionUsesClasspathClassAndMethod() {
        ToolSpec spec = new ToolSpec(
                "inventoryLookup",
                "lookup inventory",
                Map.of("type", "object"),
                ToolRef.of("file", Map.of(
                        "path", "./tools/OrderTools.java",
                        "class", "example.OrderTools",
                        "method", "query")));

        assertThatThrownBy(() -> new JavaFileToolResolver().resolve(spec))
                .isInstanceOf(UnsupportedToolRefException.class)
                .hasMessageContaining("does not support path");
    }
}
