package com.bank.financial.research.agent;

import com.bank.financial.research.calc.RevenueImpactModel;
import com.bank.financial.research.data.CompanyData;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.engine.ReportContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Sector / macro specialist. Quantifies how external information (news, prices,
 * FX, demand) flows through to revenue and earnings using a driver-based impact
 * model, rather than leaving the news as unquantified colour. Each real-time item
 * becomes a driver whose direction is its sentiment and whose magnitude is a
 * bounded first-order shock; the model decomposes the revenue/EPS impact and
 * writes it to the blackboard for the valuation and writing agents to reference.
 *
 * <p>The sentiment→shock mapping is an explicit, documented first-order heuristic
 * (shock = sentiment × {@value #SHOCK_SCALE}); a desk would replace it with
 * calibrated elasticities, but the decomposition and flow-through are real.
 */
public final class SectorMacroAgent implements ReportSubAgent {

    private static final double SHOCK_SCALE = 0.10;     // |sentiment|=1 ⇒ ±10% driver move
    private static final double REVENUE_ELASTICITY = 0.5; // revenue %-change per 1% driver move

    @Override
    public String role() {
        return "sector-macro";
    }

    @Override
    public String capability() {
        return "sector-macro";
    }

    @Override
    public void contribute(ReportContext ctx) {
        CompanyData.Dataset ds = ctx.dataset();
        if (!ds.hasFundamentals() || ds.news().isEmpty()) {
            return;
        }
        CompanyData.Fundamentals f = ds.fundamentals();

        List<RevenueImpactModel.Driver> drivers = new ArrayList<>();
        for (CompanyData.TextItem item : ds.news()) {
            double shock = item.sentiment() * SHOCK_SCALE;
            if (shock != 0) {
                drivers.add(new RevenueImpactModel.Driver(item.title(), REVENUE_ELASTICITY, shock));
            }
        }
        if (drivers.isEmpty()) {
            return;
        }

        RevenueImpactModel.ImpactResult impact = RevenueImpactModel.analyze(
                f.revenue(), f.incrementalMargin(), f.taxRate(), f.dilutedShares(), drivers);
        ctx.putNum(role(), Bb.REVENUE_IMPACT_PCT, impact.deltaRevenuePct());
        ctx.putNum(role(), Bb.EPS_IMPACT, impact.epsImpact());
    }
}
