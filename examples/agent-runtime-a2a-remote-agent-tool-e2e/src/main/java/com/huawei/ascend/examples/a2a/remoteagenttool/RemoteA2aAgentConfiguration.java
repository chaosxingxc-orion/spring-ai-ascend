package com.huawei.ascend.examples.a2a.remoteagenttool;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sample.remote-agent-tool.role", havingValue = "remote")
public class RemoteA2aAgentConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(RemoteA2aAgentConfiguration.class);

    static final String AGENT_ID = "remote-a2a-agent";

    @Bean
    AgentRuntimeHandler remoteA2aAgentHandler() {
        return new RemoteAgentHandler();
    }

    @Bean
    AgentCard remoteA2aAgentCard() {
        return AgentCard.builder()
                .name(AGENT_ID)
                .description("Remote OpenJiuwen 0.1.12 demo agent. It streams progress, asks for one more input, then completes.")
                .version("0.1.0")
                .provider(new AgentProvider("spring-ai-ascend", "http://localhost:18082"))
                .capabilities(AgentCapabilities.builder().streaming(true).pushNotifications(false).build())
                .defaultInputModes(java.util.List.of("text"))
                .defaultOutputModes(java.util.List.of("text"))
                .skills(java.util.List.of(AgentSkill.builder()
                        .id("remote-a2a-dialog")
                        .name("Remote agent dialog")
                        .description("Streams two progress messages, requests user input, then streams two more messages and completes.")
                        .tags(java.util.List.of("remote", "input-required", "streaming"))
                        .build()))
                .supportedInterfaces(java.util.List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), "/a2a")))
                .build();
    }

    private static final class RemoteAgentHandler extends OpenJiuwenAgentRuntimeHandler {
        // Remembers which remote conversations have already produced their first turn,
        // so the second turn on the same remote task/context completes regardless of the
        // user's follow-up wording. The demo no longer keys completion off magic keywords.
        private static final java.util.Set<String> STARTED_CONVERSATIONS =
                java.util.concurrent.ConcurrentHashMap.newKeySet();

        private RemoteAgentHandler() {
            super(AGENT_ID);
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            DemoAgent agent = new DemoAgent();
            agent.configure(ReActAgentConfig.builder().maxIterations(2).build());
            return agent;
        }

        @Override
        protected Iterator<Object> runOpenJiuwenAgentStreaming(BaseAgent agent, Object input, String conversationId,
                List<StreamMode> streamModes) {
            boolean firstTurn = STARTED_CONVERSATIONS.add(conversationId);
            LOG.info("remote-agent received message conversationId={} turn={} inputType={}",
                    conversationId,
                    firstTurn ? "first" : "continuation",
                    input == null ? "null" : input.getClass().getName());
            if (firstTurn) {
                return Stream.<Object>of(
                        AgentExecutionResult.output("Remote agent first stream message 1: remote task started. "),
                        AgentExecutionResult.output("Remote agent first stream message 2: more detail is needed. "),
                        AgentExecutionResult.interrupted("Remote agent needs one more user input. "
                                + "Send a follow-up message with the same parent taskId/contextId.")).iterator();
            }
            return Stream.<Object>of(
                    AgentExecutionResult.output("Remote agent second stream message 1: received user follow-up. "),
                    AgentExecutionResult.output("Remote agent second stream message 2: preparing final answer. "),
                    AgentExecutionResult.completed("Remote agent completed after the second user input.")).iterator();
        }
    }

    private static final class DemoAgent extends BaseAgent {
        private DemoAgent() {
            super(com.openjiuwen.core.singleagent.schema.AgentCard.builder()
                    .id(AGENT_ID)
                    .name(AGENT_ID)
                    .description("Demo OpenJiuwen 0.1.12 remote agent")
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
