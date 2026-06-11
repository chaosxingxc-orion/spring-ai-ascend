package com.huawei.ascend.examples.a2a.remoteopenjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
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
@ConditionalOnProperty(prefix = "sample.remote-openjiuwen.agent-a", name = "mode", havingValue = "llm")
public class AgentALlmConfiguration {

    @Bean
    Checkpointer agentALlmOpenJiuwenCheckpointer() {
        Checkpointer checkpointer = new InMemoryCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(checkpointer);
        return checkpointer;
    }

    @Bean
    OpenJiuwenAgentRuntimeHandler agentALlmHandler(
            @Value("${sample.remote-openjiuwen.agent-a.llm.model-provider:${SAA_REMOTE_OPENJIUWEN_LLM_PROVIDER:openai}}")
            String modelProvider,
            @Value("${sample.remote-openjiuwen.agent-a.llm.api-key:${SAA_REMOTE_OPENJIUWEN_LLM_API_KEY:}}")
            String apiKey,
            @Value("${sample.remote-openjiuwen.agent-a.llm.api-base:${SAA_REMOTE_OPENJIUWEN_LLM_API_BASE:}}")
            String apiBase,
            @Value("${sample.remote-openjiuwen.agent-a.llm.model-name:${SAA_REMOTE_OPENJIUWEN_LLM_MODEL:deepseek-chat}}")
            String modelName,
            @Value("${sample.remote-openjiuwen.agent-a.llm.ssl-verify:${SAA_REMOTE_OPENJIUWEN_LLM_SSL_VERIFY:true}}")
            boolean sslVerify) {
        return new AgentALlmHandler(modelProvider, required(apiKey, "SAA_REMOTE_OPENJIUWEN_LLM_API_KEY"),
                required(apiBase, "SAA_REMOTE_OPENJIUWEN_LLM_API_BASE"), modelName, sslVerify);
    }

    private static String required(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(envName + " must be set when sample.remote-openjiuwen.agent-a.mode=llm");
        }
        return value;
    }

    private static final class AgentALlmHandler extends OpenJiuwenAgentRuntimeHandler {
        private static final String SYSTEM_PROMPT = """
                You are AgentA in a remote A2A tool invocation demo.
                The runtime may provide a tool named a2a_remote_remote_b.
                When the user asks for the remote AgentB, remote input-required, or remote streaming demo, call
                a2a_remote_remote_b with a JSON argument containing a message field.
                Use this exact message value unless the user gives more specific wording:
                start remote-b streaming input-required demo
                After the tool result is returned, summarize it briefly for the user.
                """;

        private final String modelProvider;
        private final String apiKey;
        private final String apiBase;
        private final String modelName;
        private final boolean sslVerify;

        private AgentALlmHandler(
                String modelProvider,
                String apiKey,
                String apiBase,
                String modelName,
                boolean sslVerify) {
            super(AgentAConfiguration.AGENT_ID);
            this.modelProvider = modelProvider;
            this.apiKey = apiKey;
            this.apiBase = apiBase;
            this.modelName = modelName;
            this.sslVerify = sslVerify;
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            AgentCard card = AgentCard.builder()
                    .id(AgentAConfiguration.AGENT_ID)
                    .name(AgentAConfiguration.AGENT_ID)
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
