package com.huawei.ascend.examples.a2a.remoteopenjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentCards;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sample.remote-openjiuwen.role", havingValue = "a")
@ConditionalOnProperty(
        prefix = "sample.remote-openjiuwen.agent-a",
        name = "mode",
        havingValue = "deterministic",
        matchIfMissing = true)
public class AgentAConfiguration {
    static final String AGENT_ID = "local-a";

    @Bean
    OpenJiuwenAgentRuntimeHandler agentAHandler() {
        return new AgentAHandler();
    }

    @Bean
    org.a2aproject.sdk.spec.AgentCard agentACard() {
        return AgentCards.create(AGENT_ID, "Local OpenJiuwen 0.1.12 demo agent A. It calls remote AgentB as an A2A tool.");
    }

    private static final class AgentAHandler extends OpenJiuwenAgentRuntimeHandler {
        private AgentExecutionContext currentContext;

        private AgentAHandler() {
            super(AGENT_ID);
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            currentContext = context;
            DemoAgent agent = new DemoAgent();
            agent.configure(ReActAgentConfig.builder().maxIterations(2).build());
            return agent;
        }

        @Override
        protected Object runOpenJiuwenAgent(BaseAgent agent, Object input, String conversationId) {
            com.openjiuwen.core.session.interaction.InteractiveInput interactiveInput = interactiveInput(input);
            if (interactiveInput != null
                    && interactiveInput.getUserInputs() != null
                    && !interactiveInput.getUserInputs().isEmpty()) {
                return Map.of(
                        "result_type", "answer",
                        "output", "AgentA resumed from remote tool result: "
                                + interactiveInput.getUserInputs().values().iterator().next());
            }
            return Map.of(
                    "result_type", "interrupt",
                    "runtime.remote.kind", "REMOTE_AGENT_INVOCATION",
                    "runtime.remote.agentId", "remote-b",
                    "runtime.remote.toolName", "a2a_remote_remote_b",
                    "runtime.remote.toolCallId", "agent-a-tool-call-1",
                    "runtime.remote.parentTaskId", currentContext.getScope().taskId(),
                    "runtime.remote.parentContextId", currentContext.getScope().sessionId(),
                    "runtime.remote.localConversationId", conversationId,
                    "runtime.remote.arguments", Map.of("message", "start remote-b streaming input-required demo"));
        }

        private com.openjiuwen.core.session.interaction.InteractiveInput interactiveInput(Object input) {
            if (input instanceof com.openjiuwen.core.session.interaction.InteractiveInput interactiveInput) {
                return interactiveInput;
            }
            if (input instanceof Map<?, ?> inputMap
                    && inputMap.get("query") instanceof com.openjiuwen.core.session.interaction.InteractiveInput
                            interactiveInput) {
                return interactiveInput;
            }
            return null;
        }
    }

    private static final class DemoAgent extends BaseAgent {
        private DemoAgent() {
            super(AgentCard.builder()
                    .id(AGENT_ID)
                    .name(AGENT_ID)
                    .description("Demo OpenJiuwen 0.1.12 agent A")
                    .build());
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
        public Object invoke(Object input, Session session) {
            return null;
        }

        @Override
        public Iterator<Object> stream(Object input, Session session, List<StreamMode> streamModes) {
            return List.of().iterator();
        }
    }
}
