package com.bank.financial.research.bond.agent;

import com.bank.financial.research.bond.BondBb;
import com.bank.financial.research.bond.BondContext;
import com.bank.financial.research.bond.BondSubAgent;
import com.bank.financial.research.calc.BondCalc;
import com.bank.financial.research.data.BondData;

/**
 * Rates quant of the fixed-income desk. Solves YTM (bisection) and computes the
 * yield/duration/convexity set with the deterministic {@link BondCalc}, writing
 * each to the blackboard. The metrics are computed, not asserted by the model.
 */
public final class RatesAgent implements BondSubAgent {

    @Override
    public String role() {
        return "rates";
    }

    @Override
    public String capability() {
        return "rates-analytics";
    }

    @Override
    public void contribute(BondContext ctx) {
        BondData.Dataset ds = ctx.dataset();
        double ytm = BondCalc.ytm(ds.faceValue(), ds.couponRate(), ds.periodsRemaining(), ds.marketPrice());
        ctx.putNum(role(), BondBb.YTM, ytm);
        ctx.putNum(role(), BondBb.CURRENT_YIELD,
                BondCalc.currentYield(ds.faceValue(), ds.couponRate(), ds.marketPrice()));
        ctx.putNum(role(), BondBb.MACAULAY,
                BondCalc.macaulayDuration(ds.faceValue(), ds.couponRate(), ds.periodsRemaining(), ytm));
        ctx.putNum(role(), BondBb.MODIFIED,
                BondCalc.modifiedDuration(ds.faceValue(), ds.couponRate(), ds.periodsRemaining(), ytm));
        ctx.putNum(role(), BondBb.CONVEXITY,
                BondCalc.convexity(ds.faceValue(), ds.couponRate(), ds.periodsRemaining(), ytm));
    }
}
