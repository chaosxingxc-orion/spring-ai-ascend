package com.bank.financial.templates;

import com.bank.financial.kit.AbstractFinancialAgentHandler;
import com.bank.financial.kit.ModelConnection;
import com.bank.financial.kit.compliance.ComplianceRail;
import com.bank.financial.kit.compliance.KeywordScreeningBackend;
import com.bank.financial.wealth.WealthData;
import com.bank.financial.wealth.WealthTools;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.security.guardrail.GuardrailBackend;
import com.openjiuwen.core.security.guardrail.RiskLevel;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import java.util.List;

/**
 * 私人银行客户经理智能助手(面向客户经理,非客户本人)。
 *
 * <p>对应你描述的第 3 点:对 500万/1000万 级别的私行/家族办公室客户,由客户经理向客户推荐,
 * 客户经理也需要一个推荐工具。它复用同一套适当性引擎,但在【客户经理渠道】下可纳入客户符合
 * 资格的私募/家族信托(自助手机银行渠道不可见),并把结果组织成"给客户经理面谈用的推荐与话术"。
 *
 * <p>示例:u-private-c4(C4/600万,可见私募)、u-family-c5(C5/1200万,可见私募+家族信托)。
 */
public final class PrivateBankingRmCopilotAgent extends AbstractFinancialAgentHandler {

    public static final String ID = "private-banking-rm";

    private static final GuardrailBackend SCREEN = new KeywordScreeningBackend();

    public PrivateBankingRmCopilotAgent(ModelConnection model) {
        super(ID, model);
    }

    @Override
    protected int maxIterations() {
        return 5;
    }

    @Override
    protected String description() {
        return "私行客户经理助手:为高净值客户生成适当性合规的配置建议与面谈话术(含私募/家族信托)";
    }

    @Override
    protected String systemPrompt() {
        return "你是私人银行客户经理的智能助手,使用者是客户经理本人(不是客户)。"
                + "先用 get_customer_profile 了解客户的风险承受等级、资产规模与画像,"
                + "再【一次性】调用 recommend_products(不传品类即返回全部可推荐产品,不要按品类多次调用)获取"
                + "已做投资者适当性匹配、且客户符合资格的产品(可含私募/家族信托),"
                + "你只能基于该工具返回的产品给建议,绝不超出客户风险承受等级,绝不编造业绩。"
                + "输出面向客户经理:给出推荐组合、推荐理由、适当性与风险揭示要点、以及与客户面谈的话术建议。"
                + "强调最终需客户经理与客户面谈并完成适当性确认/双录后方可办理。";
    }

    @Override
    protected List<LocalFunction> tools() {
        return List.of(
                WealthTools.customerProfileTool(),
                WealthTools.recommendTool(true)); // RM channel: may include eligible private products
    }

    @Override
    protected List<AgentRail> complianceRails(AgentExecutionContext context) {
        return List.of(new ComplianceRail(
                SCREEN, RiskLevel.HIGH, context.lastUserText(), context.getScope().tenantId()));
    }

    @Override
    public String playgroundHint() {
        return "  示例客户号(就说\"为客户 2001 做配置建议\"):\n"
                + WealthData.customerRoster()
                + "  客户经理渠道:可见客户符合资格的私募/家族信托(2001可见私募;3001可见私募+家族信托)。";
    }

    public static PrivateBankingRmCopilotAgent create() {
        return new PrivateBankingRmCopilotAgent(ModelConnection.fromEnv());
    }
}
