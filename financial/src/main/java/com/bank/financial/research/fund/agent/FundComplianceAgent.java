package com.bank.financial.research.fund.agent;

import com.bank.financial.research.fund.FundContext;
import com.bank.financial.research.fund.FundSubAgent;
import java.util.ArrayList;
import java.util.List;

/**
 * Compliance gate for fund research: the fund-specific mandatory disclosures
 * (past performance ≠ future results, NAV-based, suitability) plus the AI-drafted
 * + SA-sign-off requirement.
 */
public final class FundComplianceAgent implements FundSubAgent {

    @Override
    public String role() {
        return "compliance";
    }

    @Override
    public String capability() {
        return "compliance";
    }

    @Override
    public void contribute(FundContext ctx) {
        ctx.put(role(), "compliance.noteCount", Integer.toString(notes(ctx).size()));
    }

    public List<String> notes(FundContext ctx) {
        List<String> notes = new ArrayList<>();
        notes.add("评级定义:优选=风险调整后表现领先(夏普≥1 且回撤可控);中性=表现中等;回避=夏普≤0 或回撤过深。为相对评级。");
        notes.add("业绩免责:基金过往业绩不代表未来表现,净值会波动,投资可能亏损本金。所有指标基于历史净值序列计算。");
        notes.add("适当性:基金投资须与投资者风险承受能力匹配;本报告不构成具体申购/赎回建议。");
        notes.add("数据来源:" + ctx.dataset().provenance().cite() + "(累计净值口径)。"
                + (ctx.dataset().hasBenchmark() ? "" : "无基准序列,Beta/Alpha 记为 0。"));
        notes.add("发布约束:本报告由 AI 多智能体引擎起草,指标由确定性算法计算,须经持牌人员复核签发后方可对外提供。");
        return notes;
    }
}
