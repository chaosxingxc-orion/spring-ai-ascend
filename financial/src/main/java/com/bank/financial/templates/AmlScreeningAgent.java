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
 * REFERENCE TEMPLATE — AML alert triage (BIAN domain: Financial Crime /
 * Fraud & AML). INTERNAL-facing (compliance analysts), not customer-facing.
 *
 * <p>Helps an analyst investigate a suspicious-activity alert: pulls the
 * transactions, screens names against sanctions/PEP lists, and drafts a SAR —
 * but filing the SAR always pauses for a compliance-officer sign-off. This is
 * the highest-value, structurally-safest pattern (decision support, human in
 * the loop), exactly the kind of content IFW pre-modeled for risk & compliance.
 */
public final class AmlScreeningAgent extends AbstractFinancialAgentHandler {

    public static final String ID = "aml-screening";

    private static final GuardrailBackend SCREEN = new KeywordScreeningBackend();
    private static final String API = envOr("BANK_AML_API", "http://localhost:9/api");

    public AmlScreeningAgent(ModelConnection model) {
        super(ID, model);
    }

    @Override
    protected int maxIterations() {
        return 6; // get_transactions + screen_name + (file_sar) + synthesis
    }

    @Override
    protected String description() {
        return "反洗钱告警研判:拉取交易、名单筛查、起草 SAR(上报需人工复核)";
    }

    @Override
    protected String systemPrompt() {
        return "你是银行反洗钱(AML)分析师助手,服务对象是内部合规人员。"
                + "针对可疑告警:调用工具拉取相关交易、对相关方做制裁/PEP 名单筛查,"
                + "基于事实给出研判结论与可疑点,并可起草 SAR 草稿。"
                + "所有结论必须基于工具返回的真实数据,不得臆测;最终 SAR 上报由合规官复核确认,你不自行上报。";
    }

    @Override
    protected List<LocalFunction> tools() {
        return List.of(
                HttpTool.toLocalFunction(new ToolDef(
                        "get_transactions", "拉取账户相关交易", "GET",
                        API + "/accounts/{accountId}/transactions", Map.of(),
                        Schemas.object()
                                .required("accountId", "string", "账户号")
                                .optional("days", "number", "回溯天数")
                                .build())),
                HttpTool.toLocalFunction(new ToolDef(
                        "screen_name", "对相关方做制裁/PEP 名单筛查", "POST",
                        API + "/screening", Map.of(),
                        Schemas.object().required("name", "string", "被筛查的姓名/机构名").build())),
                HttpTool.toLocalFunction(new ToolDef(
                        "file_sar", "提交可疑交易报告(SAR)", "POST",
                        API + "/sar", Map.of(),
                        Schemas.object()
                                .required("caseId", "string", "案件编号")
                                .required("narrative", "string", "SAR 叙述")
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
                new ApprovalRule(List.of("file_sar"), "SAR 上报前需合规官复核签字", null, null))));
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
