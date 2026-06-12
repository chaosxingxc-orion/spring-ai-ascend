package com.huawei.ascend.examples.a2a;

import com.huawei.ascend.runtime.engine.agentscope.AgentScopeAgent;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeEvent;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeInvocation;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.formatter.openai.OpenAIChatFormatter;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.a2aproject.sdk.spec.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "sample.a2a", name = "agent", havingValue = "agentscope", matchIfMissing = true)
public class AgentScopeE2eConfiguration {

    static final String AGENT_ID = "agentscope-react-agent";

    private static final String SYSTEM_PROMPT = """
            You are a concise assistant exposed only through the A2A protocol.
            If the user's message is exactly ping, reply exactly pong and nothing else.
            For all other messages, reply to the user's message directly and briefly.
            """;

    @Bean
    AgentScopeAgent sampleAgentScopeAgent(
            @Value("${sample.agentscope.api-key:${SAA_SAMPLE_LLM_API_KEY:sk-local-placeholder}}") String apiKey,
            @Value("${sample.agentscope.api-base:${SAA_SAMPLE_AGENTSCOPE_API_BASE:http://localhost:4000/v1}}")
            String apiBase,
            @Value("${sample.agentscope.model-name:${SAA_SAMPLE_LLM_MODEL:gpt-5.4-mini}}") String modelName,
            @Value("${sample.agentscope.endpoint-path:${SAA_SAMPLE_AGENTSCOPE_ENDPOINT_PATH:/chat/completions}}")
            String endpointPath) {
        return new SampleAgentScopeSdkAgent(AGENT_ID, apiKey, apiBase, endpointPath, modelName);
    }

    @Bean
    AgentRuntimeHandler agentScopeReactAgentHandler(AgentScopeAgent sampleAgentScopeAgent) {
        return new AgentScopeAgentRuntimeHandler(
                AGENT_ID,
                AGENT_ID,
                "Sample AgentScope ReAct agent hosted by agent-runtime.",
                sampleAgentScopeAgent);
    }


    static final class SampleAgentScopeSdkAgent implements AgentScopeAgent {
        private static final Logger LOGGER = LoggerFactory.getLogger(SampleAgentScopeSdkAgent.class);
        private static final Duration MODEL_TIMEOUT = Duration.ofSeconds(45);

        private final String name;
        private final String apiKey;
        private final String apiBase;
        private final String endpointPath;
        private final String modelName;

        SampleAgentScopeSdkAgent(
                String name,
                String apiKey,
                String apiBase,
                String endpointPath,
                String modelName) {
            this.name = Objects.requireNonNull(name, "name");
            this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
            this.apiBase = Objects.requireNonNull(apiBase, "apiBase");
            this.endpointPath = Objects.requireNonNull(endpointPath, "endpointPath");
            this.modelName = Objects.requireNonNull(modelName, "modelName");
        }

        @Override
        public Stream<AgentScopeEvent> streamEvents(AgentScopeInvocation invocation) {
            try {
                LOGGER.info("example agentscope sdk execute start tenantId={} sessionId={} taskId={} agentId={} apiBase={} model={}",
                        invocation.tenantId(),
                        invocation.sessionId(),
                        invocation.taskId(),
                        invocation.agentId(),
                        apiBase,
                        modelName);
                List<Event> events = buildAgent(invocation.agentId())
                        .stream(toAgentScopeMessages(invocation), streamOptions())
                        .collectList()
                        .block(MODEL_TIMEOUT);
                return toRuntimeEvents(events);
            } catch (Exception ex) {
                LOGGER.warn("example agentscope sdk execute failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                        invocation.tenantId(),
                        invocation.sessionId(),
                        invocation.taskId(),
                        ex.getClass().getSimpleName(),
                        errorMessage(ex));
                throw new IllegalStateException(errorMessage(ex), ex);
            }
        }

        private ReActAgent buildAgent(String agentId) {
            GenerateOptions options = GenerateOptions.builder()
                    .stream(true)
                    .temperature(0.0)
                    .maxTokens(64)
                    .build();
            OpenAIChatModel model = OpenAIChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(apiBase)
                    .endpointPath(endpointPath)
                    .modelName(modelName)
                    .stream(true)
                    .formatter(new OpenAIChatFormatter())
                    .generateOptions(options)
                    .build();
            return ReActAgent.builder()
                    .name(agentId)
                    .description("Example AgentScope ReAct agent served by agent-runtime A2A.")
                    .sysPrompt(SYSTEM_PROMPT)
                    .model(model)
                    .maxIters(3)
                    .generateOptions(options)
                    .build();
        }

        private List<Msg> toAgentScopeMessages(AgentScopeInvocation invocation) {
            List<Msg> messages = new ArrayList<>();
            for (Message message : invocation.messages()) {
                messages.add(Msg.builder()
                        .name(name)
                        .role(AgentScopeWireMessages.toMsgRole(message))
                        .textContent(AgentScopeWireMessages.text(message))
                        .metadata(Map.of(
                                "tenantId", invocation.tenantId(),
                                "sessionId", invocation.sessionId(),
                                "taskId", invocation.taskId(),
                                "agentId", invocation.agentId()))
                        .build());
            }
            if (messages.isEmpty()) {
                messages.add(Msg.builder().name(name).role(MsgRole.USER).textContent("").build());
            }
            return messages;
        }

        private StreamOptions streamOptions() {
            return StreamOptions.builder()
                    .eventTypes(EventType.AGENT_RESULT)
                    .incremental(false)
                    .build();
        }

        private Stream<AgentScopeEvent> toRuntimeEvents(List<Event> events) {
            if (events == null || events.isEmpty()) {
                return Stream.of(AgentScopeEvent.completed(""));
            }
            List<AgentScopeEvent> results = new ArrayList<>();
            StringBuilder emitted = new StringBuilder();
            String lastText = "";
            for (Event event : events) {
                String text = event.getMessage() == null ? "" : event.getMessage().getTextContent();
                lastText = text;
                if (event.isLast()) {
                    results.add(AgentScopeEvent.completed(emitted.isEmpty() ? text : ""));
                } else if (!text.isBlank()) {
                    emitted.append(text);
                    results.add(AgentScopeEvent.output(text));
                }
            }
            if (results.stream().noneMatch(event -> event.type() == AgentScopeEvent.Type.COMPLETED)) {
                results.add(AgentScopeEvent.completed(lastText));
            }
            return results.stream();
        }

        private static String errorMessage(Throwable error) {
            StringBuilder message = new StringBuilder();
            Throwable cursor = error;
            while (cursor != null) {
                String part = cursor.getMessage();
                if (part != null && !part.isBlank()) {
                    if (!message.isEmpty()) {
                        message.append(": ");
                    }
                    message.append(part);
                }
                cursor = cursor.getCause();
            }
            return message.isEmpty() ? error.getClass().getName() : message.toString();
        }
    }

}
