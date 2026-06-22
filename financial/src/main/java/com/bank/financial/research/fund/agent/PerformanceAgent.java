package com.bank.financial.research.fund.agent;

import com.bank.financial.research.calc.FundCalc;
import com.bank.financial.research.data.FundData;
import com.bank.financial.research.fund.FundBb;
import com.bank.financial.research.fund.FundContext;
import com.bank.financial.research.fund.FundSubAgent;

/**
 * Quant of the fund desk. Computes the full performance + risk metric set with the
 * deterministic {@link FundCalc} and writes each to the blackboard. The metrics are
 * computed, not asserted by the model.
 */
public final class PerformanceAgent implements FundSubAgent {

    @Override
    public String role() {
        return "performance";
    }

    @Override
    public String capability() {
        return "performance-analytics";
    }

    @Override
    public void contribute(FundContext ctx) {
        FundData.Dataset ds = ctx.dataset();
        FundCalc.FundMetrics m = FundCalc.metrics(
                ds.navs(), ds.hasBenchmark() ? ds.benchmark() : null, ds.periodsPerYear(), ds.riskFreeRate());
        ctx.putNum(role(), FundBb.CUM_RETURN, m.cumulativeReturn());
        ctx.putNum(role(), FundBb.ANN_RETURN, m.annualizedReturn());
        ctx.putNum(role(), FundBb.ANN_VOL, m.annualizedVol());
        ctx.putNum(role(), FundBb.SHARPE, m.sharpe());
        ctx.putNum(role(), FundBb.MAX_DD, m.maxDrawdown());
        ctx.putNum(role(), FundBb.CALMAR, m.calmar());
        ctx.putNum(role(), FundBb.BETA, m.beta());
        ctx.putNum(role(), FundBb.ALPHA, m.alpha());
    }
}
