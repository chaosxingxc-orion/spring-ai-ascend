package com.huawei.ascend.examples.a2a.remoteopenjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentCards;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sample.remote-openjiuwen.role", havingValue = "a")
public class AgentAConfiguration {
    static final String AGENT_ID = "local-a";

    private static final String SYSTEM_PROMPT = """
            You are AgentA in a remote A2A tool invocation demo.
            The runtime may provide a tool named a2a_remote_remote_b.
            When the user asks you to call the remote AgentB, use the a2a_remote_remote_b tool
            with a JSON argument containing a message field:
            {"message": "start remote-b streaming input-required demo"}
            After the tool result is returned, summarize it briefly for the user.
            """;

    @Bean
    OpenJiuwenAgentRuntimeHandler agentAHandler(
            @Value("${sample.remote-openjiuwen.agent-a.llm.model-provider:${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:openai}}")
            String modelProvider,
            @Value("${sample.remote-openjiuwen.agent-a.llm.api-key:${SAA_SAMPLE_LLM_API_KEY:}}")
            String apiKey,
            @Value("${sample.remote-openjiuwen.agent-a.llm.api-base:${SAA_SAMPLE_OPENJIUWEN_API_BASE:}}")
            String apiBase,
            @Value("${sample.remote-openjiuwen.agent-a.llm.model-name:${SAA_SAMPLE_LLM_MODEL:deepseek-chat}}")
            String modelName,
            @Value("${sample.remote-openjiuwen.agent-a.llm.ssl-verify:${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:true}}")
            boolean sslVerify) {
        return new AgentAHandler(modelProvider, apiKey, apiBase, modelName, sslVerify);
    }

    @Bean
    org.a2aproject.sdk.spec.AgentCard agentACard() {
        return AgentCards.create(AGENT_ID,
                "Local LLM-driven OpenJiuwen ReActAgent that calls remote AgentB as an A2A tool.");
    }

    private static final class AgentAHandler extends OpenJiuwenAgentRuntimeHandler {
        private final String modelProvider;
        private final String apiKey;
        private final String apiBase;
        private final String modelName;
        private final boolean sslVerify;

        private AgentAHandler(String modelProvider, String apiKey, String apiBase, String modelName,
                boolean sslVerify) {
            super(AGENT_ID);
            this.modelProvider = modelProvider;
            this.apiKey = apiKey;
            this.apiBase = apiBase;
            this.modelName = modelName;
            this.sslVerify = sslVerify;
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            AgentCard card = AgentCard.builder()
                    .id(AGENT_ID)
                    .name(AGENT_ID)
                    .description("LLM-driven AgentA that lets OpenJiuwen choose the remote AgentB tool.")
                    .build();
            ReActAgent agent = new ReActAgent(card);
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
