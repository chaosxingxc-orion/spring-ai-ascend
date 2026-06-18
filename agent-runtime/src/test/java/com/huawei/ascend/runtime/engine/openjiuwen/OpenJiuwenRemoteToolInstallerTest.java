package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.spi.RemoteAgentToolSpec;
import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import com.openjiuwen.harness.rails.interrupt.InterruptResult;
import com.openjiuwen.harness.rails.interrupt.RejectResult;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenJiuwenRemoteToolInstallerTest {

    @Test
    void installsRemoteToolCardAndInterruptRailOnAgentInstance() {
        RecordingAgent agent = new RecordingAgent();
        RemoteAgentToolSpec spec = toolSpec();
        OpenJiuwenRemoteToolInstaller installer = new OpenJiuwenRemoteToolInstaller(() -> List.of(spec));

        installer.install(agent, context());

        assertThat(agent.getAbilityManager().get("remote-planner")).isNotNull();
        assertThat(agent.getAbilityManager().listToolInfo())
                .anySatisfy(info -> assertThat(info.getName()).isEqualTo("remote-planner"));
        assertThat(agent.registeredRails)
                .hasSize(1)
                .first()
                .isInstanceOf(OpenJiuwenRemoteAgentInterruptRail.class);
    }

    @Test
    void railConvertsRemoteToolCallToInterruptContext() {
        RemoteAgentToolSpec spec = toolSpec();
        AgentExecutionContext context = context();
        OpenJiuwenRemoteAgentInterruptRail rail =
                new OpenJiuwenRemoteAgentInterruptRail(context, List.of(spec));
        ToolCall toolCall = ToolCall.builder()
                .id("tool-call-1")
                .name("remote-planner")
                .arguments("{\"remoteInput\":\"hello remote\"}")
                .build();
        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, null);

        assertThat(decision).isInstanceOf(InterruptResult.class);
        Object request = ((InterruptResult) decision).getRequest();
        assertThat(request).isInstanceOf(InterruptRequest.class);
        Map<String, Object> irContext =
                ((InterruptRequest) request).getContext();
        assertThat(irContext)
                .containsEntry("runtime.remote.kind", "REMOTE_AGENT_INVOCATION")
                .containsEntry("runtime.remote.agentId", "remote-planner")
                .containsEntry("runtime.remote.toolName", "remote-planner")
                .containsEntry("runtime.remote.toolCallId", "tool-call-1")
                .containsEntry("runtime.remote.parentTaskId", "task-1")
                .containsEntry("runtime.remote.parentContextId", "ctx-1")
                .containsEntry("runtime.remote.localConversationId", "conversation-1");
        Map<String, Object> arguments =
                (Map<String, Object>) irContext.get("runtime.remote.arguments");
        assertThat(arguments).containsEntry("remoteInput", "hello remote");
    }

    @Test
    void railTurnsRemoteResumeInputIntoSyntheticToolResult() {
        RemoteAgentToolSpec spec = toolSpec();
        OpenJiuwenRemoteAgentInterruptRail rail =
                new OpenJiuwenRemoteAgentInterruptRail(context(), List.of(spec));
        ToolCall toolCall = ToolCall.builder()
                .id("tool-call-1")
                .name("remote-planner")
                .arguments("{\"remoteInput\":\"hello remote\"}")
                .build();
        InteractiveInput resumeInput = new InteractiveInput();
        resumeInput.update("tool-call-1", "{\"ok\":true}");

        InterruptDecision decision = rail.resolveInterrupt(null, toolCall, resumeInput);

        assertThat(decision).isInstanceOf(RejectResult.class);
        assertThat(((RejectResult) decision).getToolResult()).toString().contains("{\"ok\":true}");
    }

    private static RemoteAgentToolSpec toolSpec() {
        return new RemoteAgentToolSpec(
                "remote-planner",
                "remote-planner",
                "Remote Planner\nPlans trips",
                Map.of(
                        "type", "object",
                        "properties", Map.of("remoteInput", Map.of("type", "string")),
                        "required", List.of("remoteInput")));
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "ctx-1", "task-1", "agent-a"),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user("start")),
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
