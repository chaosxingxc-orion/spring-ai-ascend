package com.bank.financial.research.agent;

import com.bank.financial.research.calc.Calc;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.engine.ReportContext;
import com.bank.financial.research.model.ReportModel;
import java.util.OptionalDouble;

/**
 * Lead analyst / manager — the sole decision-maker (FinCon's single-authority
 * pattern). It converts the valuation evidence into the house view: the price
 * target, the rating, and the investment thesis. When the valuation methods
 * diverge, it does not silently average — it runs an explicit reconciliation
 * (a conservative blend) and records that the target required reconciliation, so
 * the report is honest about its own uncertainty.
 */
public final class LeadManagerAgent implements ReportSubAgent {

    private static final double OVERWEIGHT_UPSIDE = 0.15;
    private static final double UNDERWEIGHT_DOWNSIDE = -0.10;

    @Override
    public String role() {
        return "lead-manager";
    }

    @Override
    public String capability() {
        return "house-view";
    }

    @Override
    public void contribute(ReportContext ctx) {
        double currentPrice = ctx.latestNum(Bb.CURRENT_PRICE).orElse(0.0);
        String verdict = ctx.latest(Bb.CONVERGENCE_VERDICT).orElse("SINGLE_METHOD");

        double priceTarget = decideTarget(ctx, verdict);
        ctx.putNum(role(), Bb.PRICE_TARGET, priceTarget);

        double upside = currentPrice > 0 ? Calc.pctChange(currentPrice, priceTarget) : 0.0;
        ctx.putNum(role(), Bb.UPSIDE_PCT, upside);

        String rating;
        if (upside >= OVERWEIGHT_UPSIDE) {
            rating = "增持 (Overweight)";
        } else if (upside <= UNDERWEIGHT_DOWNSIDE) {
            rating = "减持 (Underweight)";
        } else {
            rating = "中性 (Neutral)";
        }
        ctx.put(role(), Bb.RATING, rating);

        // Thesis prose (the one place the manager uses the model), grounded in the figures.
        String thesis = thesis(ctx, rating, priceTarget, upside, verdict);
        ctx.put(role(), Bb.THESIS, thesis);
    }

    private double decideTarget(ReportContext ctx, String verdict) {
        OptionalDouble blended = ctx.latestNum(Bb.CONVERGENCE_BLENDED);
        if (!"DIVERGENT".equals(verdict)) {
            return blended.orElse(ctx.latestNum(Bb.DCF_PER_SHARE).orElse(0.0));
        }
        // Reconciliation: methods disagree sharply → conservative midpoint of DCF & comps.
        double dcf = ctx.latestNum(Bb.DCF_PER_SHARE).orElse(0.0);
        double comps = ctx.latestNum(Bb.COMPS_MEDIAN).orElse(dcf);
        return Calc.money((dcf + comps) / 2.0);
    }

    private String thesis(ReportContext ctx, String rating, double target, double upside, String verdict) {
        if (!ctx.tryModelCall()) {
            return deterministicThesis(ctx, rating, target, upside, verdict);
        }
        String brief = "评级=" + rating + "; 目标价=" + Bb.fmt(target)
                + "; 潜在空间=" + Bb.pct(upside) + "; 收敛判定=" + verdict
                + "; DCF每股=" + ctx.latest(Bb.DCF_PER_SHARE).orElse("n/a")
                + "; 可比中位每股=" + ctx.latest(Bb.COMPS_MEDIAN).orElse("n/a")
                + "; 收入趋势(FY1)=" + ctx.latest(Bb.REVENUE_FY1).orElse("n/a")
                + "; 外部信息收入影响=" + ctx.latest(Bb.REVENUE_IMPACT_PCT).orElse("0") + "%";
        String prose = ctx.model().generate(new ReportModel.ModelTask(
                role(), "用2-3句话给出统一的投资论点(house view),以评级与目标价为锚,说明驱动与主要风险。", brief, 120));
        // Anchor the canonical numbers into the thesis so it never drifts from them.
        return prose + " 【锚定】目标价 " + Bb.fmt(target) + ",潜在空间 " + Bb.pct(upside) + "。";
    }

    private String deterministicThesis(ReportContext ctx, String rating, double target, double upside,
            String verdict) {
        return "基于三角验证(" + verdict + ")形成的房屋观点:给予「" + rating + "」评级,目标价 "
                + Bb.fmt(target) + ",对应潜在空间 " + Bb.pct(upside)
                + "。论点以盈利趋势与估值收敛为支撑,主要风险见情景与风险章节。";
    }
}
