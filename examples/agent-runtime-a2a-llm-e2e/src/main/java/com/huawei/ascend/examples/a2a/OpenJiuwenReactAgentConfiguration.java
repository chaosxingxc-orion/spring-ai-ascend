package com.huawei.ascend.examples.a2a;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentCards;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "sample.a2a", name = "agent", havingValue = "openjiuwen", matchIfMissing = true)
public class OpenJiuwenReactAgentConfiguration {

    static final String AGENT_ID = "openjiuwen-react-agent";

    @Bean
    Checkpointer openJiuwenCheckpointer(
            @Value("${sample.openjiuwen.checkpointer:${SAA_SAMPLE_OPENJIUWEN_CHECKPOINTER:in-memory}}")
            String checkpointerType,
            @Value("${sample.openjiuwen.redis-url:${SAA_SAMPLE_OPENJIUWEN_REDIS_URL:redis://localhost:6379}}")
            String redisUrl) {
        Checkpointer inMemoryCheckpointer = new InMemoryCheckpointer();
        Checkpointer redisCheckpointer = new RedisCheckpointer.Provider()
                .create(Map.of("connection", Map.of("url", redisUrl)));
        Checkpointer selected = isRedisCheckpointer(checkpointerType) ? redisCheckpointer : inMemoryCheckpointer;
        CheckpointerFactory.setDefaultCheckpointer(selected);
        return selected;
    }

    private static boolean isRedisCheckpointer(String checkpointerType) {
        return "redis".equals(String.valueOf(checkpointerType).trim().toLowerCase(Locale.ROOT));
    }

    @Bean
    MemoryProvider sampleMemoryProvider() {
        return new InMemoryMemoryProvider();
    }

    @Bean
    OpenJiuwenAgentRuntimeHandler openJiuwenReactAgentHandler(
            @Value("${sample.openjiuwen.model-provider:${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:openai}}")
            String modelProvider,
            @Value("${sample.openjiuwen.api-key:${SAA_SAMPLE_LLM_API_KEY:sk-local-placeholder}}") String apiKey,
            @Value("${sample.openjiuwen.api-base:${SAA_SAMPLE_OPENJIUWEN_API_BASE:http://localhost:4000/v1}}")
            String apiBase,
            @Value("${sample.openjiuwen.model-name:${SAA_SAMPLE_LLM_MODEL:gpt-5.4-mini}}") String modelName,
            @Value("${sample.openjiuwen.ssl-verify:${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:false}}")
            boolean sslVerify,
            MemoryProvider memoryProvider) {
        return new SampleOpenJiuwenReactAgentHandler(modelProvider, apiKey, apiBase, modelName, sslVerify,
                memoryProvider);
    }

    @Bean
    org.a2aproject.sdk.spec.AgentCard sampleDefaultAgentCard() {
        return AgentCards.create(AGENT_ID, "Sample openJiuwen ReAct agent hosted by agent-runtime.");
    }

    static final class SampleOpenJiuwenReactAgentHandler extends OpenJiuwenAgentRuntimeHandler {
        private static final String SYSTEM_PROMPT = """
                You are a concise assistant exposed only through the A2A protocol.
                If the user's message is exactly ping, reply exactly pong and nothing else.
                For all other messages, reply to the user's message directly and briefly.
                """;
        private final String modelProvider;
        private final String apiKey;
        private final String apiBase;
        private final String modelName;
        private final boolean sslVerify;
        private final MemoryProvider memoryProvider;

        SampleOpenJiuwenReactAgentHandler(
                String modelProvider,
                String apiKey,
                String apiBase,
                String modelName,
                boolean sslVerify,
                MemoryProvider memoryProvider) {
            super(AGENT_ID);
            this.modelProvider = modelProvider;
            this.apiKey = apiKey;
            this.apiBase = apiBase;
            this.modelName = modelName;
            this.sslVerify = sslVerify;
            this.memoryProvider = memoryProvider;
        }

        @Override
        protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
            // This sample uses ReActAgent, so keep the compatibility rail. DeepAgent-style
            // wiring can use openJiuwenExternalMemoryRail(...) for OpenJiuwen native memory.
            return List.of(memoryRuntimeRail(context, memoryProvider));
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            AgentCard card = AgentCard.builder()
                    .id(AGENT_ID)
                    .name(AGENT_ID)
                    .description("Example openJiuwen ReAct agent served by agent-runtime A2A.")
                    .build();
            ReActAgent agent = new ReActAgent(card);
            ReActAgentConfig config = ReActAgentConfig.builder()
                    .promptTemplate(List.of(Map.of("role", "system", "content", SYSTEM_PROMPT)))
                    .maxIterations(3)
                    .build()
                    .configureModelClient(modelProvider, apiKey, apiBase, modelName, sslVerify);
            ModelRequestConfig modelConfig = config.getModelConfigObj();
            modelConfig.setTemperature(0.0);
            modelConfig.setMaxTokens(64);
            agent.configure(config);
            return agent;
        }
    }
}
