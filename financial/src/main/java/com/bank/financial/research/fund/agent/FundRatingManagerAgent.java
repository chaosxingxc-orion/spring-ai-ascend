package com.bank.financial.research.fund.agent;

import com.bank.financial.research.calc.FundCalc;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.fund.FundBb;
import com.bank.financial.research.fund.FundContext;
import com.bank.financial.research.fund.FundSubAgent;
import com.bank.financial.research.model.ReportModel;

/**
 * Lead manager — sole decision-maker. Rebuilds the computed metrics from the
 * blackboard, applies the deterministic {@link FundCalc#rate} rule for the overall
 * rating, and forms the thesis (model prose with a deterministic fallback).
 */
public final class FundRatingManagerAgent implements FundSubAgent {

    @Override
    public String role() {
        return "lead-manager";
    }

    @Override
    public String capability() {
        return "house-view";
    }

    @Override
    public void contribute(FundContext ctx) {
        FundCalc.FundMetrics m = new FundCalc.FundMetrics(
                ctx.latestNum(FundBb.CUM_RETURN).orElse(0), ctx.latestNum(FundBb.ANN_RETURN).orElse(0),
                ctx.latestNum(FundBb.ANN_VOL).orElse(0), ctx.latestNum(FundBb.SHARPE).orElse(0),
                ctx.latestNum(FundBb.MAX_DD).orElse(0), ctx.latestNum(FundBb.CALMAR).orElse(0),
                ctx.latestNum(FundBb.BETA).orElse(0), ctx.latestNum(FundBb.ALPHA).orElse(0));
        String rating = FundCalc.rate(m).name();
        ctx.put(role(), FundBb.OVERALL_RATING, rating);
        ctx.put(role(), FundBb.THESIS, thesis(ctx, FundBb.ratingLabel(rating), m));
    }

    private String thesis(FundContext ctx, String rating, FundCalc.FundMetrics m) {
        String deterministic = "综合业绩与风险,给予「" + rating + "」评级:年化收益 " + Bb.pct(m.annualizedReturn())
                + ",年化波动 " + Bb.pct(m.annualizedVol()) + ",夏普 " + Bb.fmt(m.sharpe())
                + ",最大回撤 " + Bb.pct(m.maxDrawdown()) + "。";
        if (!ctx.tryModelCall()) {
            return deterministic;
        }
        String brief = "评级=" + rating + "; 年化收益=" + Bb.pct(m.annualizedReturn())
                + "; 年化波动=" + Bb.pct(m.annualizedVol()) + "; 夏普=" + Bb.fmt(m.sharpe())
                + "; 最大回撤=" + Bb.pct(m.maxDrawdown()) + "; Calmar=" + Bb.fmt(m.calmar());
        try {
            String prose = ctx.model().generate(new ReportModel.ModelTask(
                    "lead-manager", "用2-3句话给出基金综合观点,以评级为锚,说明收益/风险特征与适用人群。", brief, 130));
            return prose + " 【锚定】评级 " + rating + ",夏普 " + Bb.fmt(m.sharpe()) + "。";
        } catch (RuntimeException e) {
            ctx.degraded("lead-manager:thesis", e.getMessage());
            return deterministic;
        }
    }
}
