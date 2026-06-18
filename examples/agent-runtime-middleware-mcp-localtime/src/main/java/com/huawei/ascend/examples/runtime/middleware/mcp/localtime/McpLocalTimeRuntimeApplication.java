package com.huawei.ascend.examples.runtime.middleware.mcp.localtime;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
public class McpLocalTimeRuntimeApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpLocalTimeRuntimeApplication.class, args);
    }
}

@Configuration(proxyBeanMethods = false)
class McpLocalTimeRuntimeConfiguration {
    private static final String AGENT_ID = "middleware-mcp-localtime-agent";

    @Bean
    OpenJiuwenAgentRuntimeHandler mcpLocalTimeAgentHandler(
            @Value("${sample.openjiuwen.model-provider:${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:openai}}")
            String modelProvider,
            @Value("${sample.openjiuwen.api-key:${SAA_SAMPLE_LLM_API_KEY:}}") String apiKey,
            @Value("${sample.openjiuwen.api-base:${SAA_SAMPLE_OPENJIUWEN_API_BASE:https://api.deepseek.com}}")
            String apiBase,
            @Value("${sample.openjiuwen.model-name:${SAA_SAMPLE_LLM_MODEL:deepseek-chat}}") String modelName,
            @Value("${sample.openjiuwen.ssl-verify:${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:false}}")
            boolean sslVerify) {
        return new LocalTimeOpenJiuwenHandler(modelProvider, apiKey, apiBase, modelName, sslVerify);
    }

    private static final class LocalTimeOpenJiuwenHandler extends OpenJiuwenAgentRuntimeHandler {
        private static final String SYSTEM_PROMPT = """
                You are an MCP middleware example assistant.
                When the user asks for current date, current time, or local machine information,
                call the available MCP tool first.
                Answer concisely and include the tool result.
                """;

        private final String modelProvider;
        private final String apiKey;
        private final String apiBase;
        private final String modelName;
        private final boolean sslVerify;

        private LocalTimeOpenJiuwenHandler(
                String modelProvider, String apiKey, String apiBase, String modelName, boolean sslVerify) {
            super(AGENT_ID);
            this.modelProvider = modelProvider;
            this.apiKey = apiKey;
            this.apiBase = apiBase;
            this.modelName = modelName;
            this.sslVerify = sslVerify;
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            ReActAgent agent = new ReActAgent(AgentCard.builder()
                    .id(AGENT_ID)
                    .name(AGENT_ID)
                    .description("MCP local date/time middleware example")
                    .build());
            ReActAgentConfig config = ReActAgentConfig.builder()
                    .promptTemplate(List.of(Map.of("role", "system", "content", SYSTEM_PROMPT)))
                    .maxIterations(4)
                    .build()
                    .configureModelClient(modelProvider, apiKey, apiBase, modelName, sslVerify);
            ModelRequestConfig modelConfig = config.getModelConfigObj();
            modelConfig.setTemperature(0.0);
            modelConfig.setMaxTokens(512);
            agent.configure(config);
            return agent;
        }
    }
}
