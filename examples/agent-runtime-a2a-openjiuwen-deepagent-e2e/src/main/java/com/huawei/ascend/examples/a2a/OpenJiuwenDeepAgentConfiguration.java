package com.huawei.ascend.examples.a2a;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.a2a.AgentCards;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenCheckpointerConfigurer;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenDeepAgentRuntimeHandler;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenJiuwenDeepAgentConfiguration {

    static final String AGENT_ID = "openjiuwen-deep-agent";

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
    OpenJiuwenDeepAgentRuntimeHandler openJiuwenDeepAgentHandler(
            @Value("${sample.openjiuwen.model-provider:${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:openai}}")
            String modelProvider,
            @Value("${sample.openjiuwen.api-key:${SAA_SAMPLE_LLM_API_KEY:sk-local-placeholder}}") String apiKey,
            @Value("${sample.openjiuwen.api-base:${SAA_SAMPLE_OPENJIUWEN_API_BASE:http://localhost:4000/v1}}")
            String apiBase,
            @Value("${sample.openjiuwen.model-name:${SAA_SAMPLE_LLM_MODEL:gpt-5.4-mini}}") String modelName,
            @Value("${sample.openjiuwen.ssl-verify:${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:false}}")
            boolean sslVerify,
            @Value("${sample.openjiuwen.workspace-path:${SAA_SAMPLE_OPENJIUWEN_WORKSPACE_PATH:./target/deepagent-workspace}}")
            String workspacePath) {
        return new SampleOpenJiuwenDeepAgentHandler(
                modelProvider, apiKey, apiBase, modelName, sslVerify, workspacePath);
    }

    @Bean
    org.a2aproject.sdk.spec.AgentCard sampleDefaultAgentCard() {
        return AgentCards.create(AGENT_ID, "Sample openJiuwen DeepAgent hosted by agent-runtime.");
    }

    static final class SampleOpenJiuwenDeepAgentHandler extends OpenJiuwenDeepAgentRuntimeHandler {
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
        private final String workspacePath;

        SampleOpenJiuwenDeepAgentHandler(
                String modelProvider,
                String apiKey,
                String apiBase,
                String modelName,
                boolean sslVerify,
                String workspacePath) {
            super(AGENT_ID);
            this.modelProvider = modelProvider;
            this.apiKey = apiKey;
            this.apiBase = apiBase;
            this.modelName = modelName;
            this.sslVerify = sslVerify;
            this.workspacePath = workspacePath;
        }

        @Override
        protected DeepAgent createOpenJiuwenDeepAgent(AgentExecutionContext context) {
            AgentCard card = AgentCard.builder()
                    .id(AGENT_ID)
                    .name(AGENT_ID)
                    .description("Example openJiuwen DeepAgent served by agent-runtime A2A.")
                    .build();
            DeepAgentConfig config = DeepAgentConfig.builder()
                    .systemPrompt(SYSTEM_PROMPT)
                    .maxIterations(3)
                    .enableTaskLoop(true)
                    .language("en")
                    .workspacePath(workspacePath)
                    .model(modelConfig())
                    .backend(backendConfig())
                    .build();
            Workspace workspace = Workspace.builder()
                    .rootPath(workspacePath)
                    .language("en")
                    .build();
            return HarnessFactory.createDeepAgent(card, config, workspace);
        }

        private Map<String, Object> modelConfig() {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("model", modelName);
            model.put("temperature", 0.0);
            model.put("max_tokens", 64);
            return model;
        }

        private Map<String, Object> backendConfig() {
            Map<String, Object> backend = new LinkedHashMap<>();
            backend.put("provider", modelProvider);
            backend.put("api_key", apiKey);
            backend.put("api_base", apiBase);
            backend.put("verify_ssl", sslVerify);
            return backend;
        }
    }
}
