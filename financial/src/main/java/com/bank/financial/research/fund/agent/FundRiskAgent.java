package com.bank.financial.research.fund.agent;

import com.bank.financial.research.fund.FundBb;
import com.bank.financial.research.fund.FundContext;
import com.bank.financial.research.fund.FundSubAgent;

/**
 * Risk analyst. Classifies a risk level from the computed annualised volatility
 * (low &lt;12%, mid &lt;25%, else high) — a deterministic read of the quant's
 * numbers, written back for the writer and suitability sections.
 */
public final class FundRiskAgent implements FundSubAgent {

    @Override
    public String role() {
        return "risk";
    }

    @Override
    public String capability() {
        return "risk-analytics";
    }

    @Override
    public void contribute(FundContext ctx) {
        double vol = ctx.latestNum(FundBb.ANN_VOL).orElse(0.0);
        String level;
        if (vol < 0.12) {
            level = "低";
        } else if (vol < 0.25) {
            level = "中";
        } else {
            level = "高";
        }
        ctx.put(role(), FundBb.RISK_LEVEL, level);
    }
}
