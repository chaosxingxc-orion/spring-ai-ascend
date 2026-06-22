package com.bank.financial.research.bond.agent;

import com.bank.financial.research.bond.BondBb;
import com.bank.financial.research.bond.BondContext;
import com.bank.financial.research.bond.BondSubAgent;

/** Data associate: publishes the issue's terms (face / coupon / horizon / price / benchmark) to the blackboard. */
public final class BondDataAgent implements BondSubAgent {

    @Override
    public String role() {
        return "data";
    }

    @Override
    public String capability() {
        return "data-ingestion";
    }

    @Override
    public void contribute(BondContext ctx) {
        ctx.putNum(role(), BondBb.FACE_VALUE, ctx.dataset().faceValue());
        ctx.putNum(role(), BondBb.COUPON_RATE, ctx.dataset().couponRate());
        ctx.putNum(role(), BondBb.PERIODS, ctx.dataset().periodsRemaining());
        ctx.putNum(role(), BondBb.MARKET_PRICE, ctx.dataset().marketPrice());
        ctx.putNum(role(), BondBb.BENCHMARK_YIELD, ctx.dataset().benchmarkYield());
    }
}
