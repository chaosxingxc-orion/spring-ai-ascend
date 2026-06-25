package com.huawei.ascend.examples.runtime.middleware.mcp.remotejson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.mcp.HttpMcpProvider;
import com.huawei.ascend.runtime.engine.mcp.McpProperties;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenMcpToolInstaller;
import com.huawei.ascend.runtime.engine.spi.McpProvider;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
public class McpRemoteJsonRuntimeApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpRemoteJsonRuntimeApplication.class, args);
    }
}

@Configuration(proxyBeanMethods = false)
class McpRemoteJsonRuntimeConfiguration {
    private static final String AGENT_ID = "middleware-mcp-remote-json-agent";

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper remoteJsonJackson2ObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    McpProvider remoteJsonMcpProvider(
            @Value("${sample.mcp.config-file:examples/agent-runtime-middleware-mcp-remote-json/mcp-servers.example.json}")
            String configFile,
            ObjectMapper objectMapper) throws IOException {
        return new HttpMcpProvider(RemoteMcpServerConfigLoader.load(Path.of(configFile), objectMapper), objectMapper);
    }

    @Bean
    OpenJiuwenMcpToolInstaller remoteJsonMcpToolInstaller(McpProvider remoteJsonMcpProvider) {
        return new OpenJiuwenMcpToolInstaller(remoteJsonMcpProvider);
    }

    @Bean
    OpenJiuwenAgentRuntimeHandler mcpRemoteJsonAgentHandler(
            OpenJiuwenMcpToolInstaller remoteJsonMcpToolInstaller,
            @Value("${sample.openjiuwen.model-provider:${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:openai}}")
            String modelProvider,
            @Value("${sample.openjiuwen.api-key:${SAA_SAMPLE_LLM_API_KEY:}}") String apiKey,
            @Value("${sample.openjiuwen.api-base:${SAA_SAMPLE_OPENJIUWEN_API_BASE:https://api.deepseek.com}}")
            String apiBase,
            @Value("${sample.openjiuwen.model-name:${SAA_SAMPLE_LLM_MODEL:deepseek-chat}}") String modelName,
            @Value("${sample.openjiuwen.ssl-verify:${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:false}}")
            boolean sslVerify) {
        OpenJiuwenAgentRuntimeHandler handler =
                new RemoteJsonOpenJiuwenHandler(modelProvider, apiKey, apiBase, modelName, sslVerify);
        handler.setMcpToolInstaller(remoteJsonMcpToolInstaller);
        return handler;
    }

    private static final class RemoteJsonOpenJiuwenHandler extends OpenJiuwenAgentRuntimeHandler {
        private static final String SYSTEM_PROMPT = """
                You are an MCP remote-json example assistant.
                MCP tools are loaded from a JSON file at runtime.
                When the user asks a question that can be answered by an MCP tool, call the tool first.
                Answer concisely and include the tool result.
                """;

        private final String modelProvider;
        private final String apiKey;
        private final String apiBase;
        private final String modelName;
        private final boolean sslVerify;

        private RemoteJsonOpenJiuwenHandler(
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
                    .description("MCP remote JSON middleware example")
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

final class RemoteMcpServerConfigLoader {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private RemoteMcpServerConfigLoader() {
    }

    @SuppressWarnings("unchecked")
    static McpProperties load(Path configFile, ObjectMapper objectMapper) throws IOException {
        Map<String, Object> root = objectMapper.readValue(stripUtf8Bom(Files.readString(configFile)), MAP_TYPE);
        McpProperties properties = new McpProperties();
        Object servers = root.get("servers");
        if (servers instanceof List<?> serverList) {
            for (Object item : serverList) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                properties.getServers().add(toServer((Map<String, Object>) map, "remote-mcp"));
            }
        }
        Object mcpServers = root.get("mcpServers");
        if (mcpServers instanceof Map<?, ?> serverMapById) {
            for (Map.Entry<?, ?> entry : serverMapById.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> map)) {
                    continue;
                }
                properties.getServers().add(toServer((Map<String, Object>) map, String.valueOf(entry.getKey())));
            }
        }
        return properties;
    }

    private static String stripUtf8Bom(String content) {
        if (content != null && !content.isEmpty() && content.charAt(0) == '\uFEFF') {
            return content.substring(1);
        }
        return content;
    }

    private static McpProperties.Server toServer(Map<String, Object> serverMap, String fallbackServerId) {
        McpProperties.Server server = new McpProperties.Server();
        server.setServerId(stringValue(serverMap.get("serverId"), fallbackServerId));
        server.setUrl(stringValue(serverMap.get("url"), ""));
        server.setTransport(stringValue(serverMap.get("transport"), stringValue(serverMap.get("type"), "streamable-http")));
        server.setHeaders(stringMap(serverMap.get("headers")));
        String requestTimeout = stringValue(serverMap.get("requestTimeout"), "");
        if (!requestTimeout.isBlank()) {
            server.setRequestTimeout(Duration.parse(requestTimeout));
        }
        return server;
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return map.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        entry -> String.valueOf(entry.getKey()),
                        entry -> String.valueOf(entry.getValue())));
    }
}
