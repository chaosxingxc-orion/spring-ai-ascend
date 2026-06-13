package com.huawei.ascend.examples.a2a.remoteagenttool;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.a2a.AgentCards;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
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
@ConditionalOnProperty(name = "sample.remote-agent-tool.role", havingValue = "local")
public class LocalOpenJiuwenAgentConfiguration {
    static final String AGENT_ID = "local-openjiuwen";

    private static final String SYSTEM_PROMPT = """
            You are the local OpenJiuwen agent in a remote A2A tool invocation demo.
            The runtime may provide a tool named a2a_remote_remote_a2a_agent.
            When the user asks you to call the remote A2A agent, use the a2a_remote_remote_a2a_agent tool
            with a JSON argument containing a message field:
            {"message": "start remote A2A streaming input-required demo"}
            After the tool result is returned, summarize it briefly for the user.
            """;

    @Bean
    OpenJiuwenAgentRuntimeHandler localOpenJiuwenAgentHandler(
            @Value("${sample.remote-agent-tool.local-agent.llm.model-provider:${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:openai}}")
            String modelProvider,
            @Value("${sample.remote-agent-tool.local-agent.llm.api-key:${SAA_SAMPLE_LLM_API_KEY:}}")
            String apiKey,
            @Value("${sample.remote-agent-tool.local-agent.llm.api-base:${SAA_SAMPLE_OPENJIUWEN_API_BASE:}}")
            String apiBase,
            @Value("${sample.remote-agent-tool.local-agent.llm.model-name:${SAA_SAMPLE_LLM_MODEL:deepseek-chat}}")
            String modelName,
            @Value("${sample.remote-agent-tool.local-agent.llm.ssl-verify:${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:true}}")
            boolean sslVerify) {
        return new LocalAgentHandler(modelProvider, apiKey, apiBase, modelName, sslVerify);
    }

    @Bean
    org.a2aproject.sdk.spec.AgentCard localOpenJiuwenAgentCard() {
        return AgentCards.create(AGENT_ID,
                "Local LLM-driven OpenJiuwen ReActAgent that calls the remote A2A agent as a tool.");
    }

    private static final class LocalAgentHandler extends OpenJiuwenAgentRuntimeHandler {
        private final String modelProvider;
        private final String apiKey;
        private final String apiBase;
        private final String modelName;
        private final boolean sslVerify;

        private LocalAgentHandler(String modelProvider, String apiKey, String apiBase, String modelName,
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
                    .description("LLM-driven local agent that lets OpenJiuwen choose the remote A2A agent tool.")
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
