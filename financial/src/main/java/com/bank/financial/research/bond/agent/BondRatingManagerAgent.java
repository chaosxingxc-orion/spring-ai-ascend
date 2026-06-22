package com.bank.financial.research.bond.agent;

import com.bank.financial.research.bond.BondBb;
import com.bank.financial.research.bond.BondContext;
import com.bank.financial.research.bond.BondSubAgent;
import com.bank.financial.research.calc.BondCalc;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.model.ReportModel;

/**
 * Lead PM — sole decision-maker. Rebuilds the computed metrics from the blackboard,
 * applies the deterministic {@link BondCalc#stance} rule for the allocation stance,
 * and forms the thesis (model prose with a deterministic fallback).
 */
public final class BondRatingManagerAgent implements BondSubAgent {

    @Override
    public String role() {
        return "lead-manager";
    }

    @Override
    public String capability() {
        return "house-view";
    }

    @Override
    public void contribute(BondContext ctx) {
        BondCalc.BondMetrics m = new BondCalc.BondMetrics(
                ctx.latestNum(BondBb.YTM).orElse(0), ctx.latestNum(BondBb.CURRENT_YIELD).orElse(0),
                ctx.latestNum(BondBb.MACAULAY).orElse(0), ctx.latestNum(BondBb.MODIFIED).orElse(0),
                ctx.latestNum(BondBb.CONVEXITY).orElse(0), ctx.latestNum(BondBb.CREDIT_SPREAD).orElse(0));
        String stance = BondCalc.stance(m).name();
        ctx.put(role(), BondBb.STANCE, stance);
        ctx.put(role(), BondBb.THESIS, thesis(ctx, BondBb.stanceLabel(stance), m));
    }

    private String thesis(BondContext ctx, String stance, BondCalc.BondMetrics m) {
        String deterministic = "综合收益率、久期与信用利差,给予「" + stance + "」配置评级:到期收益率 "
                + Bb.pct(m.ytm()) + ",修正久期 " + Bb.fmt(m.modified()) + " 年,信用利差 "
                + Bb.pct(m.creditSpread()) + "。";
        if (!ctx.tryModelCall()) {
            return deterministic;
        }
        String brief = "配置评级=" + stance + "; 到期收益率=" + Bb.pct(m.ytm())
                + "; 当期收益率=" + Bb.pct(m.currentYield()) + "; 修正久期=" + Bb.fmt(m.modified())
                + "; 凸性=" + Bb.fmt(m.convexity()) + "; 信用利差=" + Bb.pct(m.creditSpread());
        try {
            String prose = ctx.model().generate(new ReportModel.ModelTask(
                    "lead-manager", "用2-3句话给出债券配置观点,以配置评级为锚,说明收益/久期/信用利差权衡与适用账户。", brief, 130));
            return prose + " 【锚定】配置 " + stance + ",修正久期 " + Bb.fmt(m.modified()) + " 年。";
        } catch (RuntimeException e) {
            ctx.degraded("lead-manager:thesis", e.getMessage());
            return deterministic;
        }
    }
}
