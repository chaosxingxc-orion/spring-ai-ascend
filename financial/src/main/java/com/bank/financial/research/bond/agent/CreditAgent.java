package com.bank.financial.research.bond.agent;

import com.bank.financial.research.bond.BondBb;
import com.bank.financial.research.bond.BondContext;
import com.bank.financial.research.bond.BondSubAgent;
import com.bank.financial.research.calc.BondCalc;

/**
 * Credit analyst. Computes the credit spread (issuer YTM − risk-free benchmark)
 * from the rates quant's numbers and classifies a coarse credit-risk level from the
 * spread width (tight &lt;100 bps, mid &lt;250 bps, else wide) — a deterministic read,
 * written back for the writer and allocation sections.
 */
public final class CreditAgent implements BondSubAgent {

    @Override
    public String role() {
        return "credit";
    }

    @Override
    public String capability() {
        return "credit-analytics";
    }

    @Override
    public void contribute(BondContext ctx) {
        double ytm = ctx.latestNum(BondBb.YTM).orElse(0.0);
        double bench = ctx.latestNum(BondBb.BENCHMARK_YIELD).orElse(ctx.dataset().benchmarkYield());
        double spread = BondCalc.creditSpread(ytm, bench);
        ctx.putNum(role(), BondBb.CREDIT_SPREAD, spread);

        String level;
        if (spread < 0.0100) {
            level = "低";
        } else if (spread < 0.0250) {
            level = "中";
        } else {
            level = "高";
        }
        ctx.put(role(), BondBb.CREDIT_LEVEL, level);
    }
}
