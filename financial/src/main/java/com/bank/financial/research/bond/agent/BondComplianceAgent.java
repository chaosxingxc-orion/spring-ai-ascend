package com.bank.financial.research.bond.agent;

import com.bank.financial.research.bond.BondContext;
import com.bank.financial.research.bond.BondSubAgent;
import java.util.ArrayList;
import java.util.List;

/**
 * Compliance gate for bond research: the fixed-income-specific mandatory
 * disclosures (interest-rate risk, credit/default risk, YTM-not-guaranteed) plus
 * the AI-drafted + licensed-person sign-off requirement.
 */
public final class BondComplianceAgent implements BondSubAgent {

    @Override
    public String role() {
        return "compliance";
    }

    @Override
    public String capability() {
        return "compliance";
    }

    @Override
    public void contribute(BondContext ctx) {
        ctx.put(role(), "compliance.noteCount", Integer.toString(notes(ctx).size()));
    }

    public List<String> notes(BondContext ctx) {
        List<String> notes = new ArrayList<>();
        notes.add("配置评级定义:增持/拉久期=利差补偿充分且利率风险可控;标配=收益与风险均衡;"
                + "回避/缩久期=利差过薄或利率/信用风险偏高。为相对配置建议,非买卖指令。");
        notes.add("利率风险:债券价格与市场收益率反向波动,久期越长对利率变动越敏感;"
                + "本报告修正久期与凸性基于当前条款与收益率测算,利率上行将导致价格下跌。");
        notes.add("信用风险:发行人存在违约可能,信用利差反映市场对违约与流动性的补偿要求,"
                + "评级可能被下调;持有至到期亦不能消除违约本金损失风险。");
        notes.add("到期收益率非保证:YTM 假设持有至到期且票息按该收益率再投资,实际回报受再投资利率、"
                + "提前赎回/回售条款与持有期限影响,不构成收益承诺。");
        notes.add("数据来源:" + ctx.dataset().provenance().cite() + "(单一付息频率口径,未计应计利息/税费)。");
        notes.add("发布约束:本报告由 AI 多智能体引擎起草,指标由确定性算法计算,须经持牌人员复核签发后方可对外提供。");
        return notes;
    }
}
