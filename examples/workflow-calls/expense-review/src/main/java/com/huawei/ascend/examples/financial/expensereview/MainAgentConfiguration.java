package com.huawei.ascend.examples.financial.expensereview;

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
 * <p>This agent uses an LLM to decide when to call the Expense Review
 * Workflow Agent as a remote A2A tool via the {@code review_expense} skill.
 *
 * <p>Usage:
 * <pre>
 *   # Terminal 1: Workflow Agent
 *   mvn spring-boot:run -f examples/workflow-calls/expense-review/pom.xml
 *
 *   # Terminal 2: Main Agent
 *   mvn spring-boot:run -f examples/workflow-calls/expense-review/pom.xml \
 *     -Dspring-boot.run.profiles=main
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
@Profile("main")
public class MainAgentConfiguration {

    static final String AGENT_ID = "expense-review-main";

    @Bean
    OpenJiuwenAgentRuntimeHandler mainReactAgentHandler(
            @Value("${expense-review.model-provider:openai}") String modelProvider,
            @Value("${expense-review.api-key:}") String apiKey,
            @Value("${expense-review.api-base:http://localhost:4000/v1}") String apiBase,
            @Value("${expense-review.model-name:gpt-4o-mini}") String modelName,
            @Value("${expense-review.ssl-verify:true}") boolean sslVerify) {

        return new MainReactHandler(modelProvider, apiKey, apiBase, modelName, sslVerify);
    }

    static final class MainReactHandler extends OpenJiuwenAgentRuntimeHandler {

        private static final String SYSTEM_PROMPT = """
                你是一个费用报销审核主控助手。
                你可以使用 review_expense 工具来审核报销申请。
                当用户提交报销内容时，调用 review_expense 工具。
                如果工具返回需要审批，引导用户进行审批操作。
                收到工具返回的结果后，用中文向用户总结审核结果。""";

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
                    .description("报销审核主控 ReActAgent — 通过 LLM 决策调用 Expense Review Workflow Agent")
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
