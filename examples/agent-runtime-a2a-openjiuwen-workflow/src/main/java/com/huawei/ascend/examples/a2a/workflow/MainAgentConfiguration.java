package com.huawei.ascend.examples.a2a.workflow;

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
import org.springframework.context.annotation.Profile;

/**
 * Registers the Main ReActAgent — only active with the {@code "main"} Spring profile.
 *
 * <p>This agent uses an LLM to decide when to call the Questioner Workflow
 * Agent as a remote A2A tool. The remote tool is configured via
 * {@code agent-runtime.remote-agents} in {@code application-main.yaml}.
 *
 * <p>Usage:
 * <pre>
 *   # Terminal 1: Workflow Agent
 *   mvn spring-boot:run -f examples/.../pom.xml
 *
 *   # Terminal 2: Main Agent (this profile)
 *   mvn spring-boot:run -f examples/.../pom.xml -Dspring-boot.run.profiles=main
 *
 *   # Call the main agent:
 *   curl -s -X POST http://localhost:8081/a2a -H "Content-Type: application/json" \
 *     -H "Accept: text/event-stream" \
 *     -d '{"jsonrpc":"2.0","method":"SendStreamingMessage","params":{"message":{
 *       "role":"ROLE_USER","messageId":"m1","contextId":"c1",
 *       "parts":[{"text":"帮我提个问题"}],
 *       "metadata":{"userId":"u1","agentId":"main-react-agent","sessionId":"s1"}}}}'
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
@Profile("main")
public class MainAgentConfiguration {

    static final String AGENT_ID = "main-react-agent";

    @Bean
    OpenJiuwenAgentRuntimeHandler mainReactAgentHandler(
            @Value("${sample.openjiuwen.model-provider:openai}") String modelProvider,
            @Value("${sample.openjiuwen.api-key:sk-local-placeholder}") String apiKey,
            @Value("${sample.openjiuwen.api-base:http://localhost:4000/v1}") String apiBase,
            @Value("${sample.openjiuwen.model-name:gpt-5.4-mini}") String modelName,
            @Value("${sample.openjiuwen.ssl-verify:true}") boolean sslVerify) {

        return new MainReactHandler(modelProvider, apiKey, apiBase, modelName, sslVerify);
    }

    static final class MainReactHandler extends OpenJiuwenAgentRuntimeHandler {

        private static final String SYSTEM_PROMPT = """
                你是一个主控助手。你可以使用 ask_question 工具向用户提问。
                当用户让你"提个问题"或类似请求时，调用 ask_question 工具。
                收到工具返回的结果后，告诉用户结果是什么。""";

        private final String modelProvider;
        private final String apiKey;
        private final String apiBase;
        private final String modelName;
        private final boolean sslVerify;

        MainReactHandler(String modelProvider, String apiKey, String apiBase,
                        String modelName, boolean sslVerify) {
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
                    .description("主控 ReActAgent — 通过 LLM 决策调用提问器 Workflow Agent")
                    .build();
            ReActAgent agent = new ReActAgent(card);
            ReActAgentConfig config = ReActAgentConfig.builder()
                    .promptTemplate(List.of(Map.of("role", "system", "content", SYSTEM_PROMPT)))
                    .maxIterations(5)
                    .build()
                    .configureModelClient(modelProvider, apiKey, apiBase, modelName, sslVerify);
            ModelRequestConfig modelConfig = config.getModelConfigObj();
            modelConfig.setTemperature(0.0);
            modelConfig.setMaxTokens(256);
            agent.configure(config);
            return agent;
        }
    }
}
