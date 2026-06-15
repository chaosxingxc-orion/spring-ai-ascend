package com.huawei.ascend.runtime.engine.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.interrupt.ToolCallInterruptRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenJiuwenRemoteAgentAdapterTest {

    @Test
    void remoteResumeWrapsInteractiveInputInOpenJiuwenQueryMap() {
        AgentExecutionContext context = new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "session", "task", "agent"),
                AgentExecutionContext.INPUT_TYPE_REMOTE_RESUME,
                List.of(RuntimeMessage.user("ignored")),
                Map.of(
                        AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "conversation-1",
                        AgentExecutionContext.REMOTE_TOOL_CALL_ID_VARIABLE, "tool-call-1",
                        AgentExecutionContext.REMOTE_TOOL_RESULT_VARIABLE, "{\"ok\":true}"));

        Object input = new OpenJiuwenMessageAdapter().toOpenJiuwenInput(context);

        assertThat(input).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> inputMap = (Map<String, Object>) input;
        assertThat(inputMap).containsEntry("conversation_id", "conversation-1");
        assertThat(inputMap.get("query")).isInstanceOf(InteractiveInput.class);
        InteractiveInput interactiveInput = (InteractiveInput) inputMap.get("query");
        assertThat(interactiveInput.getUserInputs()).containsEntry("tool-call-1", "{\"ok\":true}");
    }

    @Test
    void streamAdapterMapsRemoteInterruptMarkerToRemoteInvocation() {
        AgentExecutionResult result = new OpenJiuwenStreamAdapter().map(Map.of(
                "result_type", "interrupt",
                "runtime.remote.kind", "REMOTE_AGENT_INVOCATION",
                "runtime.remote.agentId", "remote-agent",
                "runtime.remote.toolName", "remote-agent",
                "runtime.remote.toolCallId", "tool-call-1",
                "runtime.remote.parentTaskId", "task-1",
                "runtime.remote.parentContextId", "ctx-1",
                "runtime.remote.localConversationId", "conversation-1",
                "runtime.remote.arguments", Map.of("message", "hello remote")));

        assertThat(result.type()).isEqualTo(AgentExecutionResult.Type.INTERRUPTED);
        assertThat(result.interruptPayload())
                .isInstanceOf(AgentExecutionResult.RemoteAgentInterrupt.class);
        assertThat(result.remoteInvocation().remoteAgentId()).isEqualTo("remote-agent");
        assertThat(result.remoteInvocation().toolCallId()).isEqualTo("tool-call-1");
        assertThat(result.remoteInvocation().arguments()).containsEntry("message", "hello remote");
    }

    @Test
    void streamAdapterMapsOpenJiuwenInterruptStateToRemoteInvocation() {
        ToolCallInterruptRequest request = new ToolCallInterruptRequest();
        request.setInterruptId("tool-call-1");
        request.setToolCallId("tool-call-1");
        request.setToolName("remote-agent");
        request.setContext(Map.of(
                "runtime.remote.kind", "REMOTE_AGENT_INVOCATION",
                "runtime.remote.agentId", "remote-agent",
                "runtime.remote.toolName", "remote-agent",
                "runtime.remote.toolCallId", "tool-call-1",
                "runtime.remote.parentTaskId", "task-1",
                "runtime.remote.parentContextId", "ctx-1",
                "runtime.remote.localConversationId", "conversation-1",
                "runtime.remote.arguments", Map.of("message", "hello remote")));

        AgentExecutionResult result = new OpenJiuwenStreamAdapter().map(Map.of(
                "result_type", "interrupt",
                "state", List.of(new OutputSchema("interaction", 0,
                        new InteractionOutput("tool-call-1", request)))));

        assertThat(result.type()).isEqualTo(AgentExecutionResult.Type.INTERRUPTED);
        assertThat(result.interruptPayload())
                .isInstanceOf(AgentExecutionResult.RemoteAgentInterrupt.class);
        assertThat(result.remoteInvocation().remoteAgentId()).isEqualTo("remote-agent");
        assertThat(result.remoteInvocation().parentTaskId()).isEqualTo("task-1");
        assertThat(result.remoteInvocation().arguments()).containsEntry("message", "hello remote");
    }

    @Test
    void streamAdapterMapsPlainOpenJiuwenInterruptToUserInputInterrupt() {
        AgentExecutionResult result = new OpenJiuwenStreamAdapter().map(Map.of(
                "result_type", "interrupt",
                "output", "please provide more input"));

        assertThat(result.type()).isEqualTo(AgentExecutionResult.Type.INTERRUPTED);
        assertThat(result.interruptPayload())
                .isInstanceOf(AgentExecutionResult.UserInputInterrupt.class);
        assertThat(result.prompt()).isEqualTo("please provide more input");
        assertThat(result.remoteInvocation()).isNull();
    }
}
