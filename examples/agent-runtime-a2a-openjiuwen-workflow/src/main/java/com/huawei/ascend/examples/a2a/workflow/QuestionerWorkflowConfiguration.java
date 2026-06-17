package com.huawei.ascend.examples.a2a.workflow;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenWorkflowAgentRuntimeHandler;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.component.llm.QuestionerConfig;

import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the Questioner Workflow Agent as a runtime handler bean.
 *
 * <p>The workflow is intentionally minimal — no LLM nodes:
 * <pre>
 *   [Start] → [Questioner: "1+1等于几?"] → [End: "你的答案是{{user_response}}，回答正确!"]
 * </pre>
 *
 * <p>The Questioner asks a fixed question (no LLM extraction), suspends for human
 * input, then the End node echoes the answer back via template rendering.
 */
@Configuration(proxyBeanMethods = false)
@org.springframework.context.annotation.Profile("!main")
public class QuestionerWorkflowConfiguration {

    static final String AGENT_ID = "questioner-workflow";

    /**
     * The question asked by the Questioner — fixed, no LLM needed.
     */
    static final String QUESTION = "请问1+1等于几？";

    @Bean
    AgentCard questionerWorkflowAgentCard() {
        return AgentCard.builder()
                .name(AGENT_ID)
                .description("提问器 Workflow Agent — 提一个固定问题，等待答案，回显答案并确认正确")
                .version("1.0")
                .provider(new AgentProvider("spring-ai-ascend", ""))
                .capabilities(AgentCapabilities.builder()
                        .streaming(true).pushNotifications(true).extendedAgentCard(false).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text", "artifact"))
                .skills(List.of(AgentSkill.builder()
                        .id("ask_question")
                        .name("ask_question")
                        .description("提问器工具。调用此工具会向用户提一个问题，等待用户回答后返回结果。输入应为简短的指令如'提一个问题'，无需提供具体问题内容。")
                        .tags(List.of())
                        .build()))
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), "/a2a")))
                .build();
    }

    @Bean
    OpenJiuwenWorkflowAgentRuntimeHandler questionerWorkflowHandler(
            @Value("${sample.openjiuwen.model-provider:openai}") String modelProvider,
            @Value("${sample.openjiuwen.api-key:sk-local-placeholder}") String apiKey,
            @Value("${sample.openjiuwen.api-base:http://localhost:4000/v1}") String apiBase,
            @Value("${sample.openjiuwen.model-name:gpt-5.4-mini}") String modelName,
            @Value("${sample.openjiuwen.ssl-verify:true}") boolean sslVerify) {

        return new QuestionerHandler(modelProvider, apiKey, apiBase, modelName, sslVerify);
    }

    static final class QuestionerHandler
            extends OpenJiuwenWorkflowAgentRuntimeHandler {

        private final String modelProvider;
        private final String apiKey;
        private final String apiBase;
        private final String modelName;
        private final boolean sslVerify;

        QuestionerHandler(String modelProvider, String apiKey, String apiBase,
                         String modelName, boolean sslVerify) {
            super(AGENT_ID);
            this.modelProvider = modelProvider;
            this.apiKey = apiKey;
            this.apiBase = apiBase;
            this.modelName = modelName;
            this.sslVerify = sslVerify;
        }

        @Override
        protected Workflow createOpenJiuwenWorkflow(AgentExecutionContext context) {
            // Model config is required for QuestionerConfig validation even though
            // the LLM is never invoked (fixed question, no field extraction).
            ModelClientConfig clientCfg = ModelClientConfig.builder()
                    .clientProvider(modelProvider)
                    .apiKey(apiKey)
                    .apiBase(apiBase)
                    .verifySsl(sslVerify)
                    .build();
            ModelRequestConfig reqCfg = ModelRequestConfig.builder()
                    .modelName(modelName)
                    .temperature(0.0)
                    .maxTokens(16)
                    .build();

            // Questioner with fixed question — no LLM extraction needed
            QuestionerConfig qCfg = new QuestionerConfig();
            qCfg.setModelClientConfig(clientCfg);
            qCfg.setModelConfig(reqCfg);
            qCfg.setResponseType("reply_directly");
            qCfg.setExtractFieldsFromResponse(false);
            qCfg.setQuestionContent(QUESTION);

            // Build DAG: Start → Questioner → End
            WorkflowCard card = WorkflowCard.builder()
                    .id("questioner")
                    .name("提问器")
                    .version("1.0")
                    .description("提一个固定问题，等待答案，回显答案并确认")
                    .build();
            Workflow wf = new Workflow(card);

            wf.setStartComp("start", new Start(),
                    Map.of("query", "${query}"), null);

            wf.addWorkflowComp("ask", new QuestionerComponent(qCfg),
                    Map.of("summary", "${start.query}"), null);

            // End passes through the user's answer — no template, just data relay
            wf.setEndComp("end", new End(),
                    Map.of("answer", "${ask.user_response}"), null);

            wf.addConnection("start", "ask");
            wf.addConnection("ask", "end");

            return wf;
        }
    }
}
