package com.bank.financial.research.fund.agent;

import com.bank.financial.research.fund.FundBb;
import com.bank.financial.research.fund.FundContext;
import com.bank.financial.research.fund.FundSubAgent;

/** Planner: fixes fund identity + outline. */
public final class FundPlannerAgent implements FundSubAgent {

    @Override
    public String role() {
        return "planner";
    }

    @Override
    public String capability() {
        return "planning";
    }

    @Override
    public void contribute(FundContext ctx) {
        ctx.put(role(), FundBb.CODE, ctx.dataset().code());
        ctx.put(role(), FundBb.NAME, ctx.dataset().name());
        ctx.put(role(), FundBb.TYPE, ctx.dataset().type());
        ctx.put(role(), FundBb.OUTLINE, FundBb.OUTLINE_DEFAULT);
    }
}
