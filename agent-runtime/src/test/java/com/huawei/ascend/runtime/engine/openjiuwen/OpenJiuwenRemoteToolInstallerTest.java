package com.huawei.ascend.runtime.engine.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.RemoteAgentCatalogPort;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class OpenJiuwenRemoteToolInstallerTest {

    @Test
    void installsRemoteToolCardAndInterruptRailOnAgentInstance() {
        RecordingAgent agent = new RecordingAgent();
        RemoteAgentCatalogPort.RemoteAgentToolSpec spec = toolSpec();
        OpenJiuwenRemoteToolInstaller installer = new OpenJiuwenRemoteToolInstaller(() -> List.of(spec));

        installer.install(agent, context());

        assertThat(agent.getAbilityManager().get("a2a_remote_remote_planner")).isNotNull();
        assertThat(agent.getAbilityManager().listToolInfo())
                .anySatisfy(info -> assertThat(info.getName()).isEqualTo("a2a_remote_remote_planner"));
        assertThat(agent.registeredRails)
                .hasSize(1)
                .first()
                .isInstanceOf(OpenJiuwenRemoteAgentInterruptRail.class);
    }

    @Test
    void railConvertsRemoteToolCallToInterruptContext() {
        RemoteAgentCatalogPort.RemoteAgentToolSpec spec = toolSpec();
        AgentExecutionContext context = context();
        OpenJiuwenRemoteAgentInterruptRail rail =
                new OpenJiuwenRemoteAgentInterruptRail(context, List.of(spec));
        ToolCall toolCall = ToolCall.builder()
                .id("tool-call-1")
                .name("a2a_remote_remote_planner")
                .arguments("{\"message\":\"hello remote\"}")
                .build();
        AgentCallbackContext callbackContext = AgentCallbackContext.builder()
                .session(AgentSessionApi.create("session-1", null, null))
                .inputs(ToolCallInputs.builder()
                        .toolCall(toolCall)
                        .toolName("a2a_remote_remote_planner")
                        .toolArgs(toolCall.getArguments())
                        .build())
                .build();

        ToolInterruptException error = assertThrows(ToolInterruptException.class,
                () -> rail.beforeToolCall(callbackContext));

        assertThat(error.getRequest().getContext())
                .containsEntry("runtime.remote.kind", "REMOTE_AGENT_INVOCATION")
                .containsEntry("runtime.remote.agentId", "remote-planner")
                .containsEntry("runtime.remote.toolName", "a2a_remote_remote_planner")
                .containsEntry("runtime.remote.toolCallId", "tool-call-1")
                .containsEntry("runtime.remote.parentTaskId", "task-1")
                .containsEntry("runtime.remote.parentContextId", "ctx-1")
                .containsEntry("runtime.remote.localConversationId", "conversation-1");
        Map<String, Object> arguments =
                (Map<String, Object>) error.getRequest().getContext().get("runtime.remote.arguments");
        assertThat(arguments).containsEntry("message", "hello remote");
    }

    @Test
    void railTurnsRemoteResumeInputIntoSyntheticToolResult() {
        RemoteAgentCatalogPort.RemoteAgentToolSpec spec = toolSpec();
        OpenJiuwenRemoteAgentInterruptRail rail =
                new OpenJiuwenRemoteAgentInterruptRail(context(), List.of(spec));
        ToolCall toolCall = ToolCall.builder()
                .id("tool-call-1")
                .name("a2a_remote_remote_planner")
                .arguments("{\"message\":\"hello remote\"}")
                .build();
        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolCall(toolCall)
                .toolName("a2a_remote_remote_planner")
                .toolArgs(toolCall.getArguments())
                .build();
        InteractiveInput resumeInput = new InteractiveInput();
        resumeInput.update("tool-call-1", "{\"ok\":true}");
        AgentCallbackContext callbackContext = AgentCallbackContext.builder()
                .session(AgentSessionApi.create("session-1", null, null))
                .inputs(inputs)
                .extra(new java.util.LinkedHashMap<>(Map.of(
                        com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState.RESUME_USER_INPUT_KEY,
                        resumeInput)))
                .build();

        rail.beforeToolCall(callbackContext);

        assertThat(callbackContext.getExtra()).containsEntry("_skip_tool", Boolean.TRUE);
        assertThat(inputs.getToolResult()).isEqualTo("{\"ok\":true}");
        assertThat(inputs.getToolMsg()).isNotNull();
        assertThat(inputs.getToolMsg().getToolCallId()).isEqualTo("tool-call-1");
        assertThat(inputs.getToolMsg().getContent()).isEqualTo("{\"ok\":true}");
    }

    private static RemoteAgentCatalogPort.RemoteAgentToolSpec toolSpec() {
        return new RemoteAgentCatalogPort.RemoteAgentToolSpec(
                "remote-planner",
                "a2a_remote_remote_planner",
                "Remote Planner\nPlans trips",
                Map.of(
                        "type", "object",
                        "properties", Map.of("message", Map.of("type", "string")),
                        "required", List.of("message")));
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "ctx-1", "task-1", "agent-a"),
                "USER_MESSAGE",
                List.of(Message.builder().role(Message.Role.ROLE_USER).parts(List.of(new TextPart("start"))).build()),
                Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "conversation-1"));
    }

    private static final class RecordingAgent extends BaseAgent {
        private final List<AgentRail> registeredRails = new ArrayList<>();

        private RecordingAgent() {
            super(AgentCard.builder().id("agent-a").name("agent-a").description("test").build());
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object getConfig() {
            return null;
        }

        @Override
        public BaseAgent registerRail(AgentRail rail) {
            registeredRails.add(rail);
            return super.registerRail(rail);
        }

        @Override
        public Object invoke(Object input, Session session) {
            return null;
        }

        @Override
        public Iterator<Object> stream(Object input, Session session, List<StreamMode> streamModes) {
            return List.of().iterator();
        }
    }
}
