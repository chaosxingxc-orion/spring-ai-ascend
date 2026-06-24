package com.huawei.ascend.examples.a2a.memory;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.a2a.AgentCards;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenCheckpointerConfigurer;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenJiuwenMemoryConfiguration {

    static final String AGENT_ID = "openjiwen-memory-agent";

    @Bean
    Checkpointer openJiuwenCheckpointer(
            @Value("${sample.openjiuwen.checkpointer:${SAA_SAMPLE_OPENJIUWEN_CHECKPOINTER:in-memory}}")
            String checkpointerType,
            @Value("${sample.openjiuwen.redis-url:${SAA_SAMPLE_OPENJIUWEN_REDIS_URL:redis://localhost:6379}}")
            String redisUrl) {
        if (!isRedisCheckpointer(checkpointerType)) {
            return OpenJiuwenCheckpointerConfigurer.setInMemoryDefault();
        }
        return setRedisCheckpointer(redisUrl);
    }

    private static boolean isRedisCheckpointer(String checkpointerType) {
        return "redis".equals(String.valueOf(checkpointerType).trim().toLowerCase(Locale.ROOT));
    }

    private static Checkpointer setRedisCheckpointer(String redisUrl) {
        Checkpointer redisCheckpointer = new RedisCheckpointer.Provider()
                .create(Map.of("connection", Map.of("url", redisUrl)));
        return OpenJiuwenCheckpointerConfigurer.setDefault(redisCheckpointer);
    }

    @Bean
    MemoryProvider jiuwenMemoryProvider(
            @Value("${sample.openjiuwen.memory-base-url:${SAA_SAMPLE_OPENJIUWEN_MEMORY_BASE_URL:http://localhost:8000}}")
            String baseUrl) {
        return new JiuwenMemoryProvider(baseUrl);
    }

    @Bean
    OpenJiuwenAgentRuntimeHandler openJiuwenMemoryAgentHandler(
            @Value("${sample.openjiuwen.model-provider:${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:dashscope}}")
            String modelProvider,
            @Value("${sample.openjiuwen.api-key:${SAA_SAMPLE_OPENJIUWEN_API_KEY:sk-local-placeholder}}") String apiKey,
            @Value("${sample.openjiuwen.api-base:${SAA_SAMPLE_OPENJIUWEN_API_BASE:https://dashscope.aliyuncs.com/compatible-mode/v1}}")
            String apiBase,
            @Value("${sample.openjiuwen.model-name:${SAA_SAMPLE_OPENJIUWEN_MODEL_NAME:qwen-max}}") String modelName,
            @Value("${sample.openjiuwen.ssl-verify:${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:false}}")
            boolean sslVerify,
            MemoryProvider memoryProvider) {
        return new MemoryAgentHandler(modelProvider, apiKey, apiBase, modelName, sslVerify, memoryProvider);
    }

    @Bean
    org.a2aproject.sdk.spec.AgentCard memoryAgentCard() {
        return AgentCards.create(AGENT_ID, "openJiuwen ReAct agent with external memory engine integration.");
    }

    static final class MemoryAgentHandler extends OpenJiuwenAgentRuntimeHandler {
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

        MemoryAgentHandler(String modelProvider, String apiKey, String apiBase,
                           String modelName, boolean sslVerify, MemoryProvider memoryProvider) {
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
            return List.of(memoryRuntimeRail(context, memoryProvider));
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            AgentCard card = AgentCard.builder()
                    .id(AGENT_ID)
                    .name(AGENT_ID)
                    .description("openJiuwen ReAct agent with external memory engine.")
                    .build();
            ReActAgent agent = new ReActAgent(card);
            ReActAgentConfig config = ReActAgentConfig.builder()
                    .promptTemplate(List.of(Map.of("role", "system", "content", SYSTEM_PROMPT)))
                    .maxIterations(3)
                    .build()
                    .configureModelClient(modelProvider, apiKey, apiBase, modelName, sslVerify);
            ModelRequestConfig modelConfig = config.getModelConfigObj();
            modelConfig.setTemperature(0.0);
            modelConfig.setMaxTokens(1024);
            agent.configure(config);
            return agent;
        }
    }
}
