package com.huawei.ascend.examples.a2a.openjiuwensimple;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a minimal openJiuwen ReActAgent as an A2A-routable handler.
 *
 * <h3>Developer guide — how to adapt your own openJiuwen agent</h3>
 *
 * <strong>Step 1: Extend {@link OpenJiuwenAgentRuntimeHandler}</strong>
 * <pre>
 * public class MyHandler extends OpenJiuwenAgentRuntimeHandler {
 *     public MyHandler() { super("my-agent-id"); }
 *
 *     {@literal @}Override
 *     protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext ctx) {
 *         // build and return your agent here
 *     }
 * }
 * </pre>
 *
 * <strong>Step 2: Implement {@code createOpenJiuwenAgent}</strong>
 * Create a {@link ReActAgent} (or DeepAgent), configure its system prompt and
 * model client, and return it. The runtime calls this method before each
 * execution.
 *
 * <strong>Step 3: Register as a Spring Bean</strong>
 * Create a {@code @Bean} method that returns your handler — that is the only
 * required bean. The runtime auto-generates an A2A
 * {@link org.a2aproject.sdk.spec.AgentCard} from the handler's {@code agentId}
 * and the optional {@code agent-runtime.access.a2a.agent-card} YAML block.
 * You can still override it with a custom {@code @Bean AgentCard} if needed.
 *
 * <p>That's it — the runtime discovers your handler bean, exposes it through
 * the A2A JSON-RPC endpoint, and handles message conversion, streaming, and
 * lifecycle automatically.
 */
@Configuration(proxyBeanMethods = false)
public class OpenJiuwenSimpleAgentConfiguration {

    static final String AGENT_ID = "openjiuwen-simple-agent";

    // ── Step 1 & 2: Handler bean — the runtime SPI bridge (the only required bean) ──
    @Bean
    OpenJiuwenAgentRuntimeHandler openJiuwenSimpleAgentHandler(
            @Value("${sample.openjiuwen.model-provider:${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:openai}}")
            String modelProvider,
            @Value("${sample.openjiuwen.api-key:${SAA_SAMPLE_LLM_API_KEY:sk-local-placeholder}}") String apiKey,
            @Value("${sample.openjiuwen.api-base:${SAA_SAMPLE_OPENJIUWEN_API_BASE:http://localhost:4000/v1}}")
            String apiBase,
            @Value("${sample.openjiuwen.model-name:${SAA_SAMPLE_LLM_MODEL:gpt-5.4-mini}}") String modelName,
            @Value("${sample.openjiuwen.ssl-verify:${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:true}}")
            boolean sslVerify) {
        return new SimpleOpenJiuwenAgentHandler(modelProvider, apiKey, apiBase, modelName, sslVerify);
    }

    // ── Step 1: Extend OpenJiuwenAgentRuntimeHandler ──
    // The only method you MUST implement is createOpenJiuwenAgent().
    static final class SimpleOpenJiuwenAgentHandler extends OpenJiuwenAgentRuntimeHandler {

        private static final String SYSTEM_PROMPT = """
                You are a helpful assistant. Answer the user's questions concisely and accurately.
                """;

        private final String modelProvider;
        private final String apiKey;
        private final String apiBase;
        private final String modelName;
        private final boolean sslVerify;

        SimpleOpenJiuwenAgentHandler(
                String modelProvider, String apiKey, String apiBase, String modelName, boolean sslVerify) {
            super(AGENT_ID);
            this.modelProvider = modelProvider;
            this.apiKey = apiKey;
            this.apiBase = apiBase;
            this.modelName = modelName;
            this.sslVerify = sslVerify;
        }

        // ── Step 2: Build and return your openJiuwen agent ──
        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            // 2a. Create an AgentCard (openJiuwen native card, not the A2A one)
            AgentCard card = AgentCard.builder()
                    .id(AGENT_ID)
                    .name(AGENT_ID)
                    .description("Minimal openJiuwen ReAct agent example.")
                    .build();

            // 2b. Create a ReActAgent with a system prompt and model configuration
            ReActAgent agent = new ReActAgent(card);
            ReActAgentConfig config = ReActAgentConfig.builder()
                    .promptTemplate(List.of(Map.of("role", "system", "content", SYSTEM_PROMPT)))
                    .maxIterations(5)
                    .build()
                    .configureModelClient(modelProvider, apiKey, apiBase, modelName, sslVerify);

            // 2c. Optional: tune model parameters
            ModelRequestConfig modelConfig = config.getModelConfigObj();
            modelConfig.setTemperature(0.7);
            modelConfig.setMaxTokens(1024);

            agent.configure(config);
            return agent;
        }
    }
}
