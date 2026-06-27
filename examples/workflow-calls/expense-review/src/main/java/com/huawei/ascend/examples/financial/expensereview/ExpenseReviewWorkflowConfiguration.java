package com.huawei.ascend.examples.financial.expensereview;

import com.huawei.ascend.examples.financial.expensereview.tool.CompanyPolicyTool;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenWorkflowAgentRuntimeHandler;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.BranchComponent;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.llm.LLMCompConfig;
import com.openjiuwen.core.workflow.component.llm.LLMComponent;
import com.openjiuwen.core.workflow.component.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.component.llm.QuestionerConfig;
import com.openjiuwen.core.workflow.component.tool.ToolComponent;
import com.openjiuwen.core.workflow.component.tool.ToolComponentConfig;

import java.util.LinkedHashMap;
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
 * Registers the Expense Review Workflow Agent as a runtime handler bean.
 *
 * <p>The workflow models an expense report review pipeline:
 * <pre>
 *   [Start] → [LLM: analyze] → [Tool: check_policy] → [LLM: audit]
 *       → [Branch: route]
 *           ├─ risk=high → [Questioner: approve] → [End]
 *           └─ else      → [LLM: auto_approve]   → [End]
 * </pre>
 *
 * <p>Active with default profile (not "main").
 */
@Configuration(proxyBeanMethods = false)
@org.springframework.context.annotation.Profile("!main")
public class ExpenseReviewWorkflowConfiguration {

    static final String AGENT_ID = "expense-review-workflow";

    private static final SystemMessage ANALYZE_PROMPT = new SystemMessage("""
            你是一个报销单分析专家。分析以下报销申请，提取所有报销条目。
            对于每个条目，提取名称(name)、金额(amount)、类别(category)。
            类别包括：交通、住宿、餐饮、其他。
            每个条目还要拆出单价(unit_price)和数量(quantity)：amount = unit_price × quantity。
            住宿的单价为每晚房价、数量为入住晚数；交通/餐饮/其他单价即该项金额、数量为1。
            同时计算总金额(total)。
            以 JSON 格式返回结果，字段为 intent("expense_report")、items(数组)、total(数字)。""");

    private static final SystemMessage AUDIT_PROMPT = new SystemMessage("""
            你是一个费用合规审核专家。根据公司费用政策和报销条目，进行合规比对。
            比对时务必对齐口径：政策限额(limit)是单价上限——住宿按每晚单价(unit_price)比对，而非总价(amount)。
            对于每个条目，判断其单价是否超出政策限额。
            如果任何条目单价超出限额，设置 risk_level 为 "high"；否则为 "none"。
            输出违规项列表(violations)和审核摘要(summary)。
            以 JSON 格式返回。""");

    private static final SystemMessage AUTO_APPROVE_PROMPT = new SystemMessage("""
            你是一个报销审核员。所有条目均在政策范围内，生成审核通过报告。
            总结报销总额、条目数，说明已通过自动审核。
            以自然语言输出。""");

    @Bean
    AgentCard expenseReviewWorkflowAgentCard() {
        return AgentCard.builder()
                .name(AGENT_ID)
                .description("费用报销审核 Workflow Agent — 分析报销→查政策→合规比对→审批")
                .version("1.0")
                .provider(new AgentProvider("spring-ai-ascend", ""))
                .capabilities(AgentCapabilities.builder()
                        .streaming(true).pushNotifications(true).extendedAgentCard(false).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text", "artifact"))
                .skills(List.of(AgentSkill.builder()
                        .id("review_expense")
                        .name("review_expense")
                        .description("审核报销申请：分析条目、查询政策、比对合规性、人工审批/自动通过")
                        .tags(List.of())
                        .build()))
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), "/a2a")))
                .build();
    }

    @Bean
    OpenJiuwenWorkflowAgentRuntimeHandler expenseReviewWorkflowHandler(
            @Value("${expense-review.model-provider:openai}") String modelProvider,
            @Value("${expense-review.api-key:}") String apiKey,
            @Value("${expense-review.api-base:http://localhost:4000/v1}") String apiBase,
            @Value("${expense-review.model-name:gpt-4o-mini}") String modelName,
            @Value("${expense-review.ssl-verify:true}") boolean sslVerify) {

        return new ExpenseReviewHandler(modelProvider, apiKey, apiBase, modelName, sslVerify);
    }

    static final class ExpenseReviewHandler
            extends OpenJiuwenWorkflowAgentRuntimeHandler {

        private final String modelProvider;
        private final String apiKey;
        private final String apiBase;
        private final String modelName;
        private final boolean sslVerify;

        ExpenseReviewHandler(String modelProvider, String apiKey, String apiBase,
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
            // ── Shared model config ────────────────────────────
            ModelClientConfig clientCfg = ModelClientConfig.builder()
                    .clientProvider(modelProvider)
                    .apiKey(apiKey)
                    .apiBase(apiBase)
                    .verifySsl(sslVerify)
                    .build();
            ModelRequestConfig reqCfg = ModelRequestConfig.builder()
                    .modelName(modelName)
                    .temperature(0.0)
                    .maxTokens(1024)
                    .build();

            // ── Build Workflow DAG ─────────────────────────────
            WorkflowCard card = WorkflowCard.builder()
                    .id("expense-review")
                    .name("费用报销审核")
                    .version("1.0")
                    .description("分析报销→查政策→合规比对→审批")
                    .build();
            Workflow wf = new Workflow(card);

            // ── start ──────────────────────────────────────────
            wf.setStartComp("start", new Start(),
                    Map.of("query", "${query}"), null);

            // ── analyze (LLM) ──────────────────────────────────
            LLMCompConfig analyzeCfg = new LLMCompConfig();
            analyzeCfg.setModelClientConfig(clientCfg);
            analyzeCfg.setModelConfig(reqCfg);
            analyzeCfg.setSystemPromptTemplate(ANALYZE_PROMPT);
            // Prompt templates use {{local_binding}} (PromptTemplate default delimiters);
            // node input bindings below use ${node.field} (graph-engine ref paths) — two distinct mechanisms.
            analyzeCfg.setUserPromptTemplate(
                    new UserMessage("请分析以下报销申请：{{query}}"));
            analyzeCfg.setResponseFormat(new LinkedHashMap<>(Map.of(
                    "type", "json"
            )));
            analyzeCfg.setOutputConfig(new LinkedHashMap<>(Map.of(
                    "intent", Map.of("type", "string", "description", "意图类型"),
                    "items", Map.of("type", "array", "description", "报销条目列表",
                            "items", Map.of("type", "object", "properties", Map.of(
                                    "name", Map.of("type", "string", "description", "条目名称"),
                                    "amount", Map.of("type", "number", "description", "金额(总价)"),
                                    "unit_price", Map.of("type", "number", "description", "单价(住宿为每晚)"),
                                    "quantity", Map.of("type", "number", "description", "数量(住宿为晚数)"),
                                    "category", Map.of("type", "string", "description", "类别")))),
                    "total", Map.of("type", "number", "description", "总金额")
            )));
            wf.addWorkflowComp("analyze", new LLMComponent(analyzeCfg),
                    Map.of("query", "${start.query}"), null);

            // ── check_policy (Tool) ────────────────────────────
            Tool policyTool = new CompanyPolicyTool();
            ToolComponentConfig toolCfg = new ToolComponentConfig();
            ToolComponent toolComp = new ToolComponent(toolCfg).bindTool(policyTool);
            wf.addWorkflowComp("check_policy", toolComp,
                    Map.of("items", "${analyze.items}"), null);

            // ── audit (LLM) ────────────────────────────────────
            LLMCompConfig auditCfg = new LLMCompConfig();
            auditCfg.setModelClientConfig(clientCfg);
            auditCfg.setModelConfig(reqCfg);
            auditCfg.setSystemPromptTemplate(AUDIT_PROMPT);
            auditCfg.setUserPromptTemplate(
                    new UserMessage("报销条目：{{items}}\n公司政策：{{policy_rules}}"));
            auditCfg.setResponseFormat(new LinkedHashMap<>(Map.of(
                    "type", "json"
            )));
            auditCfg.setOutputConfig(new LinkedHashMap<>(Map.of(
                    "violations", Map.of("type", "array", "description", "违规项列表",
                            "items", Map.of("type", "object", "properties", Map.of(
                                    "item", Map.of("type", "string", "description", "违规条目名称"),
                                    "rule", Map.of("type", "string", "description", "违反的政策规则"),
                                    "gap", Map.of("type", "number", "description", "超出金额")))),
                    "risk_level", Map.of("type", "string", "description", "风险等级: high 或 none"),
                    "summary", Map.of("type", "string", "description", "审核摘要")
            )));
            // Tool nodes wrap their result map under a "data" key (ToolComponentOutput.RESTFUL_DATA),
            // so the policy fields live at ${check_policy.data.policy_rules}, not the top level.
            wf.addWorkflowComp("audit", new LLMComponent(auditCfg),
                    Map.of("items", "${analyze.items}",
                           "policy_rules", "${check_policy.data.policy_rules}"), null);

            // ── route (Branch) ─────────────────────────────────
            BranchComponent branch = new BranchComponent();
            // Path A: risk_level == "high" → human approval
            branch.addBranch("${audit.risk_level} == \"high\"",
                    "approve", "high_risk");
            // Path B: fallback → auto approve
            branch.addBranch("true",
                    "auto_approve", "low_risk");
            wf.addWorkflowComp("route", branch,
                    Map.of("risk_level", "${audit.risk_level}"), null);

            // ── approve (Questioner) — Path A ──────────────────
            QuestionerConfig qCfg = new QuestionerConfig();
            qCfg.setModelClientConfig(clientCfg);
            qCfg.setModelConfig(reqCfg);
            qCfg.setResponseType("reply_directly");
            qCfg.setExtractFieldsFromResponse(false);
            qCfg.setQuestionContent("费用报销审核需要您的审批。请审核后输入 'approved' 通过，或说明拒绝理由。");
            wf.addWorkflowComp("approve", new QuestionerComponent(qCfg),
                    Map.of("summary", "${audit.summary}",
                           "violations", "${audit.violations}"), null);

            // ── auto_approve (LLM) — Path B ────────────────────
            LLMCompConfig autoCfg = new LLMCompConfig();
            autoCfg.setModelClientConfig(clientCfg);
            autoCfg.setModelConfig(reqCfg);
            autoCfg.setSystemPromptTemplate(AUTO_APPROVE_PROMPT);
            autoCfg.setUserPromptTemplate(
                    new UserMessage("审核结果：{{summary}}\n请生成审核通过报告。"));
            autoCfg.setResponseFormat(new LinkedHashMap<>(Map.of(
                    "type", "text"
            )));
            autoCfg.setOutputConfig(new LinkedHashMap<>(Map.of(
                    "text", Map.of("type", "string", "description", "审核通过报告")
            )));
            wf.addWorkflowComp("auto_approve", new LLMComponent(autoCfg),
                    Map.of("summary", "${audit.summary}"), null);

            // ── end ────────────────────────────────────────────
            wf.setEndComp("end", new End(),
                    Map.of("result", "${approve.user_response}",
                           "auto_result", "${auto_approve.text}"), null);

            // ── edges ───────────────────────────────────────────
            wf.addConnection("start", "analyze");
            wf.addConnection("analyze", "check_policy");
            wf.addConnection("check_policy", "audit");
            wf.addConnection("audit", "route");
            // Branch targets handled by BranchComponent.addConditionalEdges internally.
            // Outbound edges from branch targets to end:
            wf.addConnection("approve", "end");
            wf.addConnection("auto_approve", "end");

            return wf;
        }
    }
}
