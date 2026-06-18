package com.bank.financial.templates;

import com.bank.financial.kit.AbstractFinancialAgentHandler;
import com.bank.financial.kit.ModelConnection;
import com.bank.financial.kit.approval.RuleBasedApprovalRail;
import com.bank.financial.kit.compliance.ComplianceRail;
import com.bank.financial.kit.compliance.KeywordScreeningBackend;
import com.bank.financial.kit.spec.AgentDefinition.ApprovalRule;
import com.bank.financial.kit.spec.AgentDefinition.ToolDef;
import com.bank.financial.kit.tool.HttpTool;
import com.bank.financial.kit.tool.Schemas;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.security.guardrail.GuardrailBackend;
import com.openjiuwen.core.security.guardrail.RiskLevel;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import java.util.List;
import java.util.Map;

/**
 * REFERENCE TEMPLATE — Card servicing (BIAN domain: Card / Customer Servicing).
 *
 * <p>A customer-facing credit-card assistant: reads the bill from a backend
 * (never invents figures), explains charges, and assists with repayment — with
 * a human-approval gate on large repayments. Copy this class, rename, point the
 * URLs at your APIs, and either register it in {@code PlaygroundCatalog} to try
 * it, or publish it as a {@code @Bean OpenJiuwenAgentRuntimeHandler} to serve it.
 *
 * <p>Demonstrates all four kit seams: prompt, tools (declarative HTTP),
 * compliance (block at HIGH+), approval (amount threshold).
 */
public final class CreditCardServicingAgent extends AbstractFinancialAgentHandler {

    public static final String ID = "credit-card-servicing";

    private static final GuardrailBackend SCREEN = new KeywordScreeningBackend();
    private static final String API = envOr("BANK_CARD_API", "http://localhost:9/api");

    public CreditCardServicingAgent(ModelConnection model) {
        super(ID, model);
    }

    @Override
    protected String description() {
        return "信用卡智能客服:查账单、解读消费、协助分期与还款";
    }

    @Override
    protected String systemPrompt() {
        return "你是某银行的信用卡客服。用简洁准确的中文回答账单、消费、分期、还款问题。"
                + "涉及金额、利率、额度时必须调用后端工具查询,绝不凭空编造数字。"
                + "动账类操作(还款、分期)需走审批,你只负责发起与说明。";
    }

    @Override
    protected List<LocalFunction> tools() {
        return List.of(
                HttpTool.toLocalFunction(new ToolDef(
                        "query_bill", "查询信用卡当期账单", "GET",
                        API + "/cards/{cardId}/bill", Map.of(),
                        Schemas.object().required("cardId", "string", "信用卡号后四位").build())),
                HttpTool.toLocalFunction(new ToolDef(
                        "repay", "发起信用卡还款", "POST",
                        API + "/cards/{cardId}/repay", Map.of(),
                        Schemas.object()
                                .required("cardId", "string", "信用卡号后四位")
                                .required("amount", "number", "还款金额(元)")
                                .build())));
    }

    @Override
    protected List<AgentRail> complianceRails(AgentExecutionContext context) {
        return List.of(new ComplianceRail(
                SCREEN, RiskLevel.HIGH, context.lastUserText(), context.getScope().tenantId()));
    }

    @Override
    protected List<AgentRail> approvalRails(AgentExecutionContext context) {
        return List.of(new RuleBasedApprovalRail(List.of(
                new ApprovalRule(List.of("repay"), "大额还款需经理复核", 50_000.0, "amount"))));
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
