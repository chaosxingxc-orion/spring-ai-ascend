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
 * REFERENCE TEMPLATE — Loan application intake (BIAN domain: Consumer Loan /
 * Credit Management).
 *
 * <p>A multi-turn intake assistant: collects the applicant's details, checks
 * application status from a backend, and submits the application — with a
 * mandatory human review before submission (no auto-approval of credit). The
 * multi-turn collection is driven by the system prompt; durable cross-session
 * resume would add a checkpointer (see the guide).
 */
public final class LoanIntakeAgent extends AbstractFinancialAgentHandler {

    public static final String ID = "loan-intake";

    private static final GuardrailBackend SCREEN = new KeywordScreeningBackend();
    private static final String API = envOr("BANK_LOAN_API", "http://localhost:9/api");

    public LoanIntakeAgent(ModelConnection model) {
        super(ID, model);
    }

    @Override
    protected int maxIterations() {
        return 5; // per turn: ask-more / check / submit + answer (collection spans turns)
    }

    @Override
    protected String description() {
        return "贷款申请受理:资料收集、预审说明、进度查询、提交申请";
    }

    @Override
    protected String systemPrompt() {
        return "你是某银行的贷款申请受理助手。逐步收集申请人必要信息(姓名、证件号、贷款用途、"
                + "金额、期限、收入),信息不全时礼貌追问,集齐后再调用 submit_application 提交。"
                + "你不做授信决策、不承诺批复结果;利率/额度以后端为准,不得编造。"
                + "征信查询需获得申请人授权后方可进行。";
    }

    @Override
    protected List<LocalFunction> tools() {
        return List.of(
                HttpTool.toLocalFunction(new ToolDef(
                        "check_status", "查询贷款申请进度", "GET",
                        API + "/loan-applications/{applicationId}", Map.of(),
                        Schemas.object().required("applicationId", "string", "申请编号").build())),
                HttpTool.toLocalFunction(new ToolDef(
                        "submit_application", "提交贷款申请", "POST",
                        API + "/loan-applications", Map.of(),
                        Schemas.object()
                                .required("applicantName", "string", "申请人姓名")
                                .required("idNumber", "string", "证件号")
                                .required("amount", "number", "申请金额(元)")
                                .required("termMonths", "number", "期限(月)")
                                .optional("purpose", "string", "贷款用途")
                                .build())));
    }

    @Override
    protected List<AgentRail> complianceRails(AgentExecutionContext context) {
        return List.of(new ComplianceRail(
                SCREEN, RiskLevel.HIGH, context.lastUserText(), context.getScope().tenantId()));
    }

    @Override
    protected List<AgentRail> approvalRails(AgentExecutionContext context) {
        // Every submission pauses for a human reviewer — credit is never auto-granted.
        return List.of(new RuleBasedApprovalRail(List.of(
                new ApprovalRule(List.of("submit_application"), "贷款申请提交前需信贷员复核", null, null))));
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
