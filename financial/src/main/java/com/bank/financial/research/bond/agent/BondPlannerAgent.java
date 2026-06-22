package com.bank.financial.research.bond.agent;

import com.bank.financial.research.bond.BondBb;
import com.bank.financial.research.bond.BondContext;
import com.bank.financial.research.bond.BondSubAgent;

/** Planner: fixes bond identity + outline. */
public final class BondPlannerAgent implements BondSubAgent {

    @Override
    public String role() {
        return "planner";
    }

    @Override
    public String capability() {
        return "planning";
    }

    @Override
    public void contribute(BondContext ctx) {
        ctx.put(role(), BondBb.CODE, ctx.dataset().code());
        ctx.put(role(), BondBb.NAME, ctx.dataset().name());
        ctx.put(role(), BondBb.ISSUER, ctx.dataset().issuer());
        ctx.put(role(), BondBb.RATING, ctx.dataset().rating());
        ctx.put(role(), BondBb.OUTLINE, BondBb.OUTLINE_DEFAULT);
    }
}
