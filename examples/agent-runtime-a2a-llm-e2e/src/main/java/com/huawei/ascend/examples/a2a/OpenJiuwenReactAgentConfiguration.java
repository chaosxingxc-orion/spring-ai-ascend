package com.huawei.ascend.examples.a2a;

import com.huawei.ascend.runtime.engine.adapters.openjiuwen.OpenJiuwenAgentDriver;
import com.huawei.ascend.runtime.engine.spi.AgentDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a single openJiuwen {@link AgentDriver} bean. The runtime's neutral execution core
 * ({@code AgentDriverRegistry} -> {@code RunCoordinator} -> {@code OpenJiuwenOutputConverter})
 * drives it directly through the A2A access path — no legacy {@code AgentHandler} bridge.
 */
@Configuration(proxyBeanMethods = false)
public class OpenJiuwenReactAgentConfiguration {

    static final String AGENT_ID = "openjiuwen-react-agent";

    private static final String SYSTEM_PROMPT = """
            You are a concise assistant exposed only through the A2A protocol.
            If the user's message is exactly ping, reply exactly pong and nothing else.
            For all other messages, reply to the user's message directly and briefly.
            """;

    @Bean
    AgentDriver openJiuwenReactAgentDriver(
            @Value("${sample.openjiuwen.model-provider:${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:openai}}")
            String modelProvider,
            @Value("${sample.openjiuwen.api-key:${SAA_SAMPLE_LLM_API_KEY:sk-x00550472}}") String apiKey,
            @Value("${sample.openjiuwen.api-base:${SAA_SAMPLE_OPENJIUWEN_API_BASE:http://localhost:4000/v1}}")
            String apiBase,
            @Value("${sample.openjiuwen.model-name:${SAA_SAMPLE_LLM_MODEL:gpt-5.4-mini}}") String modelName,
            @Value("${sample.openjiuwen.ssl-verify:${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:false}}")
            boolean sslVerify) {
        return new OpenJiuwenAgentDriver(
                AGENT_ID, SYSTEM_PROMPT, modelProvider, apiKey, apiBase, modelName, sslVerify);
    }
}
