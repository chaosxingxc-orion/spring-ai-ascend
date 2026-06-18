package com.bank.financial.templates;

import com.bank.financial.kit.AbstractFinancialAgentHandler;
import com.bank.financial.kit.ModelConnection;
import com.bank.financial.kit.approval.RuleBasedApprovalRail;
import com.bank.financial.kit.compliance.ComplianceRail;
import com.bank.financial.kit.compliance.KeywordScreeningBackend;
import com.bank.financial.kit.spec.AgentDefinition.ApprovalRule;
import com.bank.financial.kit.tool.LocalTool;
import com.bank.financial.kit.tool.Schemas;
import com.bank.financial.wealth.WealthData;
import com.bank.financial.wealth.WealthTools;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.security.guardrail.GuardrailBackend;
import com.openjiuwen.core.security.guardrail.RiskLevel;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import java.util.List;
import java.util.Map;

/**
 * 零售理财顾问(手机银行,面向海量个人客户,千人千面)。
 *
 * <p>它把你描述的零售场景落成代码:① 产品货架含短期理财/公募基金/黄金/短期贷款;
 * ② <b>投资者适当性</b>由 {@code recommend_products} 在代码侧强制(产品风险≤客户 C 等级),
 * 模型拿不到超风险产品;③ <b>资产分层</b>——私募/家族信托不在自助渠道直售,符合私行资格的
 * 客户引导至专属客户经理(见 {@link PrivateBankingRmCopilotAgent});④ 申购为敏感动作,
 * 需风险揭示与人工确认(approval rail)。
 *
 * <p>示例客户:u-mass-c2(C2/8万)、u-mass-c4(C4/80万)、u-private-c4(C4/600万,私行)。
 */
public final class RetailWealthAdvisorAgent extends AbstractFinancialAgentHandler {

    public static final String ID = "retail-wealth-advisor";

    private static final GuardrailBackend SCREEN = new KeywordScreeningBackend();

    public RetailWealthAdvisorAgent(ModelConnection model) {
        super(ID, model);
    }

    @Override
    protected int maxIterations() {
        return 5; // profile → recommend(one call) → (place_order) → answer
    }

    @Override
    protected String description() {
        return "手机银行零售理财顾问:依客户画像与风险等级,做适当性匹配的个性化推荐";
    }

    @Override
    protected String systemPrompt() {
        return "你是手机银行的零售理财顾问,服务个人客户。流程必须是:"
                + "1) 先用 get_customer_profile 了解客户的风险承受等级(C1-C5)、资产规模与画像;"
                + "2) 再【一次性】调用 recommend_products(不传品类即返回全部可推荐产品,不要按品类多次调用)获取"
                + "已做投资者适当性匹配的产品,你只能推荐该工具返回的产品,绝不推荐高于客户风险承受等级的产品,绝不编造收益率;"
                + "3) 结合客户画像做千人千面的解释与配置建议,并做风险揭示(理财非存款,投资有风险)。"
                + "若客户符合私行资格,引导其联系专属客户经理获取私募/家族信托等专属服务,不要在本渠道直接推荐这些产品。"
                + "客户确认申购时调用 place_order,该操作需经风险揭示与人工确认。";
    }

    @Override
    protected List<LocalFunction> tools() {
        return List.of(
                WealthTools.customerProfileTool(),
                WealthTools.recommendTool(false), // self-service channel: public shelf only
                placeOrderTool());
    }

    @Override
    protected List<AgentRail> complianceRails(AgentExecutionContext context) {
        return List.of(new ComplianceRail(
                SCREEN, RiskLevel.HIGH, context.lastUserText(), context.getScope().tenantId()));
    }

    @Override
    protected List<AgentRail> approvalRails(AgentExecutionContext context) {
        // 申购前必须风险揭示 + 人工确认(可对接双录)。
        return List.of(new RuleBasedApprovalRail(List.of(
                new ApprovalRule(List.of("place_order"), "理财申购需风险揭示与客户确认(双录)", null, null))));
    }

    /** Demo subscribe tool — in production this hits the order/booking system. */
    private static LocalFunction placeOrderTool() {
        return LocalTool.of("place_order", "提交理财产品申购",
                Schemas.object()
                        .required("customerId", "string", "客户号")
                        .required("productId", "string", "产品代码")
                        .required("amount", "number", "申购金额(元)")
                        .build(),
                inputs -> {
                    Object pid = inputs.get("productId");
                    return Map.of("status", "submitted", "productId", pid == null ? "" : pid,
                            "note", "申购已提交(演示),实际以后端确认为准。");
                });
    }

    @Override
    public String playgroundHint() {
        return "  示例客户号(就说\"我是客户 1001\"或\"帮 2001 推荐理财\"):\n"
                + WealthData.customerRoster()
                + "  规则:只会推荐风险等级≤客户C等级的产品;私募/家族信托仅客户经理渠道(private-banking-rm)。";
    }

    /** Convenience for serving/playground: build with env-based model settings. */
    public static RetailWealthAdvisorAgent create() {
        return new RetailWealthAdvisorAgent(ModelConnection.fromEnv());
    }
}
