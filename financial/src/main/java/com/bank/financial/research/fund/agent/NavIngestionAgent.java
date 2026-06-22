package com.bank.financial.research.fund.agent;

import com.bank.financial.research.fund.FundBb;
import com.bank.financial.research.fund.FundContext;
import com.bank.financial.research.fund.FundSubAgent;

/** Data associate: publishes the NAV-series shape (count + annualisation) to the blackboard. */
public final class NavIngestionAgent implements FundSubAgent {

    @Override
    public String role() {
        return "data";
    }

    @Override
    public String capability() {
        return "data-ingestion";
    }

    @Override
    public void contribute(FundContext ctx) {
        ctx.putNum(role(), FundBb.NAV_POINTS, ctx.dataset().navs().size());
        ctx.putNum(role(), FundBb.PERIODS_PER_YEAR, ctx.dataset().periodsPerYear());
    }
}
