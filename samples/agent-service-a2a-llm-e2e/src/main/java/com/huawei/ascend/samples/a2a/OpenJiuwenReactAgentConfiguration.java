package com.huawei.ascend.samples.a2a;

import com.huawei.ascend.runtime.dispatch.adapter.openjiuwen.OpenJiuwenAgentHandler;
import com.huawei.ascend.runtime.dispatch.handler.AgentExecutionContext;
import com.huawei.ascend.runtime.dispatch.spi.AgentHandler;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenJiuwenReactAgentConfiguration {

    static final String AGENT_ID = "openjiuwen-react-agent";

    @Bean
    AgentHandler openJiuwenReactAgentHandler(
            @Value("${sample.openjiuwen.model-provider:${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:openai}}")
            String modelProvider,
            @Value("${sample.openjiuwen.api-key:${SAA_SAMPLE_LLM_API_KEY:}}") String apiKey,
            @Value("${sample.openjiuwen.api-base:${SAA_SAMPLE_OPENJIUWEN_API_BASE:http://localhost:4000/v1}}")
            String apiBase,
            @Value("${sample.openjiuwen.model-name:${SAA_SAMPLE_LLM_MODEL:gpt-5.4-mini}}") String modelName,
            @Value("${sample.openjiuwen.ssl-verify:${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:false}}")
            boolean sslVerify) {
        return new SampleOpenJiuwenReactAgentHandler(modelProvider, apiKey, apiBase, modelName, sslVerify);
    }

    static final class SampleOpenJiuwenReactAgentHandler extends OpenJiuwenAgentHandler {
        private static final Logger LOGGER = LoggerFactory.getLogger(SampleOpenJiuwenReactAgentHandler.class);
        private static final String SYSTEM_PROMPT = """
                You are a concise assistant exposed only through the A2A protocol.
                Reply to the user's message directly and briefly.
                """;

        private final String modelProvider;
        private final String apiKey;
        private final String apiBase;
        private final String modelName;
        private final boolean sslVerify;

        SampleOpenJiuwenReactAgentHandler(
                String modelProvider,
                String apiKey,
                String apiBase,
                String modelName,
                boolean sslVerify) {
            super(AGENT_ID);
            this.modelProvider = modelProvider;
            this.apiKey = apiKey;
            this.apiBase = apiBase;
            this.modelName = modelName;
            this.sslVerify = sslVerify;
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            try {
                LOGGER.info("sample openjiuwen execute start tenantId={} sessionId={} taskId={} agentId={} provider={} apiBase={} model={}",
                        context.getScope().tenantId(),
                        context.getScope().sessionId(),
                        context.getScope().taskId(),
                        context.getScope().agentId(),
                        modelProvider,
                        apiBase,
                        modelName);
                ReActAgent agent = buildAgent();
                Object input = toOpenJiuwenInput(context);
                Object result = Runner.runAgent(agent, input, null, null);
                LOGGER.info("sample openjiuwen execute finished tenantId={} sessionId={} taskId={} resultType={}",
                        context.getScope().tenantId(),
                        context.getScope().sessionId(),
                        context.getScope().taskId(),
                        result == null ? "null" : result.getClass().getName());
                return Stream.of(result);
            } catch (Exception e) {
                LOGGER.warn("sample openjiuwen execute failed tenantId={} sessionId={} taskId={} errorClass={} message={}",
                        context.getScope().tenantId(),
                        context.getScope().sessionId(),
                        context.getScope().taskId(),
                        e.getClass().getSimpleName(),
                        errorMessage(e));
                throw new IllegalStateException(errorMessage(e), e);
            } finally {
                safeRelease(context);
            }
        }

        private ReActAgent buildAgent() {
            AgentCard card = AgentCard.builder()
                    .id(AGENT_ID)
                    .name(AGENT_ID)
                    .description("Sample openJiuwen ReAct agent served by agent-service A2A.")
                    .build();
            ReActAgent agent = new ReActAgent(card);
            ReActAgentConfig config = ReActAgentConfig.builder()
                    .promptTemplate(List.of(Map.of("role", "system", "content", SYSTEM_PROMPT)))
                    .maxIterations(3)
                    .build()
                    .configureModelClient(modelProvider, apiKey, apiBase, modelName, sslVerify);
            ModelRequestConfig modelConfig = config.getModelConfigObj();
            modelConfig.setTemperature(0.1);
            modelConfig.setMaxTokens(256);
            agent.configure(config);
            return agent;
        }
    }
}
