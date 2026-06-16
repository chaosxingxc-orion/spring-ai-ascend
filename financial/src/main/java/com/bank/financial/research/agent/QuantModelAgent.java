package com.bank.financial.research.agent;

import com.bank.financial.research.calc.Calc;
import com.bank.financial.research.calc.EarningsSurprise;
import com.bank.financial.research.calc.TrendForecast;
import com.bank.financial.research.data.CompanyData;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.engine.ReportContext;

/**
 * Quant / modelling associate. Turns the raw fundamentals into the forward
 * estimates the report rests on, using real computation, not narrative:
 * <ul>
 *   <li>a forward revenue trend by multi-method convergence (CAGR / OLS /
 *       momentum), whose blended growth becomes the model's growth assumption;</li>
 *   <li>FY1 revenue / EPS / FCF estimates;</li>
 *   <li>the standardized earnings surprise (SUE) of the latest actual vs
 *       consensus.</li>
 * </ul>
 * Every figure is written to the blackboard under a key the quant owns.
 */
public final class QuantModelAgent implements ReportSubAgent {

    @Override
    public String role() {
        return "quant-model";
    }

    @Override
    public String capability() {
        return "financial-modeling";
    }

    @Override
    public void contribute(ReportContext ctx) {
        CompanyData.Dataset ds = ctx.dataset();
        if (!ds.hasFundamentals()) {
            return; // no fundamentals → no model; orchestrator records the gap
        }
        CompanyData.Fundamentals f = ds.fundamentals();

        // Forward revenue trend: converge CAGR / OLS / momentum into one view.
        double growth;
        if (f.revenueHistory().size() >= 2) {
            TrendForecast.ForecastResult trend = TrendForecast.project(f.revenueHistory(), 1);
            ctx.putNum(role(), Bb.TREND_BLENDED, trend.blended());
            ctx.put(role(), Bb.TREND_CONVERGENT, Boolean.toString(trend.convergent()));
            ctx.putNum(role(), Bb.REVENUE_FY1, trend.blended());
            growth = Calc.pctChange(f.revenue(), trend.blended());
        } else {
            growth = 0.08; // fallback assumption when history is unavailable
            ctx.putNum(role(), Bb.REVENUE_FY1, f.revenue() * (1 + growth));
        }
        ctx.putNum(role(), Bb.GROWTH, growth);

        // FY1 EPS: prefer consensus mean (the market's FY1 number) when present.
        double epsFy1 = ds.hasConsensus() ? ds.consensus().epsMean() : f.eps() * (1 + growth);
        ctx.putNum(role(), Bb.EPS_FY1, epsFy1);
        ctx.putNum(role(), Bb.FCF_FY1, f.fcfBase() * (1 + growth));

        // Standardized Unexpected Earnings: latest actual EPS vs consensus dispersion.
        if (ds.hasConsensus() && ds.consensus().epsStdev() > 0) {
            EarningsSurprise.SurpriseResult sue =
                    EarningsSurprise.sue(f.eps(), ds.consensus().epsMean(), ds.consensus().epsStdev());
            ctx.putNum(role(), Bb.SUE, sue.sue());
            ctx.put(role(), Bb.SUE_CLASS, sue.classification().name());
        }
    }
}
