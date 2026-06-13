package com.huawei.ascend.runtime.engine.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.junit.jupiter.api.Test;

class A2aParentTaskProjectorTest {

    private final A2aParentTaskProjector projector = new A2aParentTaskProjector();

    /**
     * Remote failure text routinely carries newlines and quotes (stack traces,
     * SSL alerts); the tool result handed back to the framework must stay
     * machine-parseable JSON with the original text intact.
     */
    @Test
    void failedRemoteErrorTextSurvivesJsonRoundTrip() throws Exception {
        String text = "line one\nline two\twith \"quotes\" and a backslash \\";
        A2aParentTaskProjector.RemoteOutcome outcome = projector.projectRemoteOutcome(
                invocation(),
                List.of(new RemoteAgentInvocationService.RemoteAgentResult(
                        RemoteAgentInvocationService.RemoteAgentResult.Type.FAILED,
                        text, "remote-task-1", "remote-ctx-1", Map.of())),
                mock(AgentEmitter.class));

        JsonNode parsed = new ObjectMapper().readTree(outcome.toolResult());
        assertThat(parsed.get("error").asText()).isEqualTo(text);
        assertThat(parsed.has("code")).isFalse();
    }

    @Test
    void failedRemoteResultCodePassesThroughToToolResult() throws Exception {
        A2aParentTaskProjector.RemoteOutcome outcome = projector.projectRemoteOutcome(
                invocation(),
                List.of(new RemoteAgentInvocationService.RemoteAgentResult(
                        RemoteAgentInvocationService.RemoteAgentResult.Type.FAILED,
                        "remote A2A stream timed out", "remote-task-1", "remote-ctx-1",
                        Map.of("code", "REMOTE_TIMEOUT", "retryable", true))),
                mock(AgentEmitter.class));

        JsonNode parsed = new ObjectMapper().readTree(outcome.toolResult());
        assertThat(parsed.get("error").asText()).isEqualTo("remote A2A stream timed out");
        assertThat(parsed.get("code").asText()).isEqualTo("REMOTE_TIMEOUT");
    }

    private static AgentExecutionResult.RemoteInvocation invocation() {
        return new AgentExecutionResult.RemoteInvocation(
                "remote-agent", "remote-agent", "tool-call-1",
                "task-1", "ctx-1", "conversation-1", Map.of("message", "hello"));
    }
}
