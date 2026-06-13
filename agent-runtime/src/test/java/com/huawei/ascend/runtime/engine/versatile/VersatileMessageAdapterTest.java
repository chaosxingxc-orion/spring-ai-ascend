package com.huawei.ascend.runtime.engine.versatile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies AgentExecutionContext → VersatileHttpRequest conversion,
 * including URL template resolution, header merging, and body assembly
 * with metadata-driven input fields.
 */
class VersatileMessageAdapterTest {

    private static VersatileProperties properties() {
        VersatileProperties props = new VersatileProperties();
        props.setUrl("http://7.213.200.213:3001/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}");
        props.setUrlVariables(Map.of("project_id", "mock_project_id", "agent_id", "agent-001"));
        props.setQueryParams(Map.of("type", "controller", "workspace_id", "10"));
        props.setHeaders(Map.of("content-type", "application/json", "stream", "true"));
        props.setPassthroughHeaders(List.of("x-invoke-mode"));
        props.setInputMetadataKeys(List.of("intent", "wap_userName"));
        props.setTimeout(Duration.ofSeconds(30));
        return props;
    }

    private static AgentExecutionContext context(
            String sessionId, String userText, Map<String, Object> variables) {
        RuntimeIdentity scope = new RuntimeIdentity(
                "default", "test-user", sessionId, "task-001", "versatile-agent");
        List<RuntimeMessage> messages = List.of(RuntimeMessage.user(userText));
        return new AgentExecutionContext(scope, "USER_MESSAGE", messages, variables);
    }

    @Test
    void resolvesUrlTemplate() {
        VersatileMessageAdapter adapter = new VersatileMessageAdapter(properties());
        AgentExecutionContext ctx = context("conv-123", "hi", Map.of());

        VersatileHttpRequest req = adapter.toRequest(ctx);

        assertThat(req.url())
                .startsWith("http://7.213.200.213:3001/v1/mock_project_id/agents/agent-001/conversations/conv-123?")
                .contains("type=controller")
                .contains("workspace_id=10");
    }

    @Test
    void rejectsPathTraversalInConversationId() {
        VersatileMessageAdapter adapter = new VersatileMessageAdapter(properties());
        AgentExecutionContext ctx = context("../etc/passwd", "hi", Map.of());

        assertThatThrownBy(() -> adapter.toRequest(ctx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    void buildsBodyWithQueryAndMetadataFields() {
        VersatileMessageAdapter adapter = new VersatileMessageAdapter(properties());
        AgentExecutionContext ctx = context("conv-1", "预订酒店",
                Map.of("intent", "LATEST", "wap_userName", "张三"));

        VersatileHttpRequest req = adapter.toRequest(ctx);

        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) req.body().get("inputs");
        assertThat(inputs).containsEntry("query", "预订酒店");
        assertThat(inputs).containsEntry("intent", "LATEST");
        assertThat(inputs).containsEntry("wap_userName", "张三");
    }

    @Test
    void omitsMissingMetadataKeysFromBody() {
        VersatileMessageAdapter adapter = new VersatileMessageAdapter(properties());
        AgentExecutionContext ctx = context("conv-1", "查询", Map.of());

        VersatileHttpRequest req = adapter.toRequest(ctx);

        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) req.body().get("inputs");
        assertThat(inputs).containsOnlyKeys("query");
    }

    @Test
    void mergesHeadersWithPassthroughOverride() {
        VersatileMessageAdapter adapter = new VersatileMessageAdapter(properties());
        // A2A client passes x-invoke-mode which overrides the empty YAML default
        AgentExecutionContext ctx = context("conv-1", "hi",
                Map.of("x-invoke-mode", "DEBUG"));

        VersatileHttpRequest req = adapter.toRequest(ctx);

        // YAML header preserved
        assertThat(req.headers()).containsEntry("content-type", "application/json");
        // Passthrough from A2A metadata overrides (though not present in YAML, so it's additive here)
        assertThat(req.headers()).containsEntry("X-Invoke-Mode", "DEBUG");
    }

    @Test
    void extractsLastUserMessageAsQuery() {
        VersatileMessageAdapter adapter = new VersatileMessageAdapter(properties());
        AgentExecutionContext ctx = context("conv-1",
                "{\"person_name\":\"李四\",\"checkin_date\":\"2026-03-30\"}", Map.of());

        VersatileHttpRequest req = adapter.toRequest(ctx);

        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) req.body().get("inputs");
        assertThat(inputs.get("query").toString()).contains("李四");
    }

    @Test
    void alwaysUsesPostMethod() {
        VersatileMessageAdapter adapter = new VersatileMessageAdapter(properties());
        VersatileHttpRequest req = adapter.toRequest(context("c1", "hi", Map.of()));

        assertThat(req.method()).isEqualTo("POST");
    }
}
