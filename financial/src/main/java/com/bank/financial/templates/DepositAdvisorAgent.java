package com.bank.financial.templates;

import com.bank.financial.kit.AbstractFinancialAgentHandler;
import com.bank.financial.kit.ModelConnection;
import com.bank.financial.kit.compliance.ComplianceRail;
import com.bank.financial.kit.compliance.KeywordScreeningBackend;
import com.bank.financial.kit.tool.LocalTool;
import com.bank.financial.kit.tool.Schemas;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.security.guardrail.GuardrailBackend;
import com.openjiuwen.core.security.guardrail.RiskLevel;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教程示例:定期存款顾问。约 30 行业务代码,演示"用 spring-ai-ascend 开发一个 Agent"的
 * 最小完整闭环——继承基类、填提示词、挂一个【确定性】工具(利息计算)、加合规护栏。
 * 数字全部来自工具(防幻觉),且结果可重复验证(10万存1年 @1.5% = 利息1500元)。
 *
 * <p>对照本目录 {@code TUTORIAL.cn.md} 一步步看。
 */
public final class DepositAdvisorAgent extends AbstractFinancialAgentHandler {

    public static final String ID = "deposit-advisor";

    private static final GuardrailBackend SCREEN = new KeywordScreeningBackend();

    /** 整存整取年利率表(示意):期限(月) -> 年利率. */
    private static final Map<Integer, Double> RATES = Map.of(
            3, 0.011, 6, 0.013, 12, 0.015, 24, 0.017, 36, 0.0195);

    public DepositAdvisorAgent(ModelConnection model) {
        super(ID, model);
    }

    @Override
    protected int maxIterations() {
        return 3; // quote_deposit → answer; no need for more
    }

    @Override
    protected String description() {
        return "定期存款顾问:介绍存款产品、按真实利率表测算利息";
    }

    @Override
    protected String systemPrompt() {
        return "你是银行的定期存款顾问。客户询问存款时,必须调用 quote_deposit 工具按真实利率表"
                + "测算利率与利息,绝不自己编造利率或金额。给出建议时做风险提示:定期存款受存款保险"
                + "保障(50万元限额内本息全额),提前支取按活期计息。";
    }

    @Override
    protected List<LocalFunction> tools() {
        return List.of(LocalTool.of("quote_deposit",
                "按期限测算定期存款利率与到期利息",
                Schemas.object()
                        .required("principal", "number", "本金(元)")
                        .required("termMonths", "number", "存期(月):3/6/12/24/36")
                        .build(),
                this::quoteDeposit));
    }

    @Override
    protected List<AgentRail> complianceRails(AgentExecutionContext context) {
        return List.of(new ComplianceRail(
                SCREEN, RiskLevel.HIGH, context.lastUserText(), context.getScope().tenantId()));
    }

    /** The deterministic tool body — same input always yields the same output. */
    private Object quoteDeposit(Map<String, Object> in) {
        double principal = num(in.get("principal"));
        int months = (int) num(in.get("termMonths"));
        Double rate = RATES.get(months);
        if (rate == null) {
            return Map.of("error", "暂不支持该存期", "supportedTermMonths", RATES.keySet());
        }
        double interest = principal * rate * (months / 12.0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("principal", principal);
        out.put("termMonths", months);
        out.put("annualRate", rate);
        out.put("estimatedInterest", Math.round(interest * 100) / 100.0);
        out.put("maturityAmount", Math.round((principal + interest) * 100) / 100.0);
        out.put("note", "受存款保险保障(50万元限额内本息全额);提前支取按活期计息。");
        return out;
    }

    private static double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v));
    }

    @Override
    public String playgroundHint() {
        return "  试试:\"我有10万,存1年利息多少?\" 或 \"50万存3年怎么样\"(支持存期 3/6/12/24/36 个月)";
    }

    public static DepositAdvisorAgent create() {
        return new DepositAdvisorAgent(ModelConnection.fromEnv());
    }
}
