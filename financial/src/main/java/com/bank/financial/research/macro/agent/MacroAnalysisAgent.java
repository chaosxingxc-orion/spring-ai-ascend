package com.bank.financial.research.macro.agent;

import com.bank.financial.research.calc.MacroCalc;
import com.bank.financial.research.macro.MacroBb;
import com.bank.financial.research.macro.MacroContext;
import com.bank.financial.research.macro.MacroSubAgent;

/**
 * Quant analysis: run the deterministic {@link MacroCalc} over the indicators and
 * write the growth/inflation/activity/liquidity scores + composite to the
 * blackboard. The numbers are computed here; the writer only narrates them.
 */
public final class MacroAnalysisAgent implements MacroSubAgent {

    @Override
    public String role() {
        return "analysis";
    }

    @Override
    public String capability() {
        return "macro-analytics";
    }

    @Override
    public void contribute(MacroContext ctx) {
        MacroCalc.MacroStance s = MacroCalc.assess(ctx.dataset());
        ctx.putNum(role(), MacroBb.SCORE_GROWTH, s.growth());
        ctx.putNum(role(), MacroBb.SCORE_INFLATION, s.inflation());
        ctx.putNum(role(), MacroBb.SCORE_ACTIVITY, s.activity());
        ctx.putNum(role(), MacroBb.SCORE_LIQUIDITY, s.liquidity());
        ctx.putNum(role(), MacroBb.COMPOSITE, s.composite());
        // tilt is the strategist's call to publish as the house view, but record the
        // computed suggestion so the chain is auditable.
        ctx.put(role(), "macro.tilt.computed", s.tilt().name());
    }
}
